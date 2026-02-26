import { Injectable, signal, computed, inject, effect, NgZone, DestroyRef } from '@angular/core';
import { WebSocketService } from './websocket.service';
import { Participant, RemotePeer, WebRtcSignalEnvelope } from '../models/room.model';

const ICE_SERVERS: RTCIceServer[] = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' },
];

const MAX_WEBRTC_PARTICIPANTS = 6;

@Injectable({ providedIn: 'root' })
export class WebRtcService {
  private readonly ws = inject(WebSocketService);
  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);

  private readonly peerConnections = new Map<string, RTCPeerConnection>();
  private readonly peerNicknames = new Map<string, string>();
  private localStreamInternal: MediaStream | null = null;

  readonly localStream = signal<MediaStream | null>(null);
  readonly remoteStreams = signal<RemotePeer[]>([]);
  readonly isCameraOn = signal(false);
  readonly isMicOn = signal(false);
  readonly mediaError = signal<string | null>(null);

  readonly isActive = computed(() => this.localStream() !== null);

  constructor() {
    // Handle incoming WebRTC signals
    effect(() => {
      const signal = this.ws.webRtcSignal();
      if (!signal) return;
      this.handleSignal(signal);
    });

    // Handle participant list changes for peer discovery & cleanup
    effect(() => {
      const participants = this.ws.participants();
      if (!this.isActive()) return;
      // Ensure we know our own connectionId before reconciling
      const myId = this.ws.myConnectionId();
      if (myId) {
        this._myConnectionId = myId;
      }
      this.reconcilePeers(participants);
    });

    this.destroyRef.onDestroy(() => this.stop());
  }

  async start(): Promise<void> {
    try {
      this.mediaError.set(null);
      // Set our connectionId from the WebSocket service
      const myId = this.ws.myConnectionId();
      if (myId) {
        this._myConnectionId = myId;
      }
      const stream = await navigator.mediaDevices.getUserMedia({
        video: true,
        audio: true,
      });
      this.localStreamInternal = stream;
      this.localStream.set(stream);
      this.isCameraOn.set(true);
      this.isMicOn.set(true);

      // Initiate connections to all existing participants
      const participants = this.ws.participants();
      this.reconcilePeers(participants);
    } catch (err) {
      const message = err instanceof DOMException
        ? `Camera/microphone access denied: ${err.message}`
        : 'Failed to access media devices';
      this.mediaError.set(message);
      throw err;
    }
  }

  stop(): void {
    // Close all peer connections
    for (const [id, pc] of this.peerConnections) {
      pc.close();
    }
    this.peerConnections.clear();
    this.peerNicknames.clear();
    this.remoteStreams.set([]);

    // Stop local media tracks
    if (this.localStreamInternal) {
      this.localStreamInternal.getTracks().forEach(t => t.stop());
      this.localStreamInternal = null;
    }
    this.localStream.set(null);
    this.isCameraOn.set(false);
    this.isMicOn.set(false);
    this.mediaError.set(null);
  }

  toggleCamera(): void {
    if (!this.localStreamInternal) return;
    const videoTrack = this.localStreamInternal.getVideoTracks()[0];
    if (videoTrack) {
      videoTrack.enabled = !videoTrack.enabled;
      this.isCameraOn.set(videoTrack.enabled);
    }
  }

  toggleMic(): void {
    if (!this.localStreamInternal) return;
    const audioTrack = this.localStreamInternal.getAudioTracks()[0];
    if (audioTrack) {
      audioTrack.enabled = !audioTrack.enabled;
      this.isMicOn.set(audioTrack.enabled);
    }
  }

  private reconcilePeers(participants: Participant[]): void {
    if (!this.localStreamInternal) return;

    // Find our own connectionId from the participants list
    const myConnectionId = this.findMyConnectionId(participants);
    if (!myConnectionId) return;

    // Only consider up to MAX participants (excluding ourselves)
    const otherParticipants = participants
      .filter(p => p.connectionId !== myConnectionId)
      .slice(0, MAX_WEBRTC_PARTICIPANTS - 1);

    const activeIds = new Set(otherParticipants.map(p => p.connectionId));

    // Remove stale connections
    for (const [id, pc] of this.peerConnections) {
      if (!activeIds.has(id)) {
        pc.close();
        this.peerConnections.delete(id);
        this.peerNicknames.delete(id);
      }
    }

    // Create connections to new peers (we initiate if our connectionId < theirs to avoid duplicates)
    for (const participant of otherParticipants) {
      this.peerNicknames.set(participant.connectionId, participant.nickname);
      if (!this.peerConnections.has(participant.connectionId) && myConnectionId < participant.connectionId) {
        this.createPeerConnection(participant.connectionId, true);
      }
    }

    this.updateRemoteStreams();
  }

  private findMyConnectionId(participants: Participant[]): string | null {
    return this._myConnectionId;
  }

  private _myConnectionId: string | null = null;

  private createPeerConnection(remoteConnectionId: string, initiator: boolean): void {
    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
    this.peerConnections.set(remoteConnectionId, pc);

    // Add local tracks
    if (this.localStreamInternal) {
      for (const track of this.localStreamInternal.getTracks()) {
        pc.addTrack(track, this.localStreamInternal);
      }
    }

    // Handle ICE candidates
    pc.onicecandidate = (event) => {
      if (event.candidate) {
        this.ws.sendWebRtcIceCandidate(
          remoteConnectionId,
          event.candidate.candidate,
          event.candidate.sdpMid,
          event.candidate.sdpMLineIndex,
        );
      }
    };

    // Handle remote tracks
    pc.ontrack = (event) => {
      this.zone.run(() => {
        const existingPeer = this.remoteStreams().find(p => p.connectionId === remoteConnectionId);
        if (!existingPeer || existingPeer.stream !== event.streams[0]) {
          this.updateRemoteStreams();
        }
      });
    };

    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'failed' || pc.connectionState === 'closed') {
        this.peerConnections.delete(remoteConnectionId);
        this.peerNicknames.delete(remoteConnectionId);
        this.zone.run(() => this.updateRemoteStreams());
      }
    };

    if (initiator) {
      this.createAndSendOffer(pc, remoteConnectionId);
    }
  }

  private async createAndSendOffer(pc: RTCPeerConnection, remoteConnectionId: string): Promise<void> {
    try {
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      this.ws.sendWebRtcOffer(remoteConnectionId, offer.sdp!);
    } catch (err) {
      console.error('Failed to create WebRTC offer:', err);
    }
  }

  private async handleSignal(signal: WebRtcSignalEnvelope): Promise<void> {
    const fromId = signal.fromConnectionId;

    switch (signal.type) {
      case 'offer': {
        // Create a peer connection if we don't have one for this peer
        if (!this.peerConnections.has(fromId)) {
          this.createPeerConnection(fromId, false);
        }
        const pc = this.peerConnections.get(fromId);
        if (!pc || !signal.sdp) return;

        try {
          await pc.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp: signal.sdp }));
          const answer = await pc.createAnswer();
          await pc.setLocalDescription(answer);
          this.ws.sendWebRtcAnswer(fromId, answer.sdp!);
        } catch (err) {
          console.error('Failed to handle WebRTC offer:', err);
        }
        break;
      }
      case 'answer': {
        const pc = this.peerConnections.get(fromId);
        if (!pc || !signal.sdp) return;
        try {
          await pc.setRemoteDescription(new RTCSessionDescription({ type: 'answer', sdp: signal.sdp }));
        } catch (err) {
          console.error('Failed to handle WebRTC answer:', err);
        }
        break;
      }
      case 'ice-candidate': {
        const pc = this.peerConnections.get(fromId);
        if (!pc || !signal.candidate) return;
        try {
          await pc.addIceCandidate(new RTCIceCandidate({
            candidate: signal.candidate,
            sdpMid: signal.sdpMid ?? undefined,
            sdpMLineIndex: signal.sdpMLineIndex ?? undefined,
          }));
        } catch (err) {
          console.error('Failed to add ICE candidate:', err);
        }
        break;
      }
    }
  }

  private updateRemoteStreams(): void {
    const peers: RemotePeer[] = [];
    for (const [connectionId, pc] of this.peerConnections) {
      const receivers = pc.getReceivers();
      let stream: MediaStream | null = null;
      if (receivers.length > 0) {
        stream = new MediaStream(receivers.map(r => r.track));
      }
      peers.push({
        connectionId,
        nickname: this.peerNicknames.get(connectionId) ?? 'Unknown',
        stream,
      });
    }
    this.remoteStreams.set(peers);
  }
}
