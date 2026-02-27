import { Injectable, signal, computed, inject, effect, NgZone, DestroyRef, untracked } from '@angular/core';
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
  private readonly pendingIceCandidates = new Map<string, RTCIceCandidateInit[]>();
  private localStreamInternal: MediaStream | null = null;

  readonly localStream = signal<MediaStream | null>(null);
  readonly remoteStreams = signal<RemotePeer[]>([]);
  readonly isCameraOn = signal(false);
  readonly isMicOn = signal(false);
  readonly mediaError = signal<string | null>(null);

  readonly isActive = computed(() => this.localStream() !== null);

  private _myConnectionId: string | null = null;

  constructor() {
    // Process queued WebRTC signals
    effect(() => {
      const signals = this.ws.webRtcSignal();
      if (signals.length === 0) return;
      const toProcess = [...signals];
      untracked(() => this.ws.webRtcSignal.set([]));
      for (const sig of toProcess) {
        this.handleSignal(sig);
      }
    }, { allowSignalWrites: true });

    // Reconcile peers when participants change AND we have something to do
    // (either our camera is on, or someone else has camera on)
    let previousParticipantCount = 0;
    effect(() => {
      const participants = this.ws.participants();
      const cameraStates = this.ws.peerCameraStates();
      const myId = this.ws.myConnectionId();
      if (myId) {
        this._myConnectionId = myId;
      }
      if (!this._myConnectionId) return;

      // Re-broadcast our camera state when a new participant joins
      if (participants.length > previousParticipantCount && this.isCameraOn()) {
        this.ws.sendCameraState(true);
      }
      previousParticipantCount = participants.length;

      const hasLocalStream = this.localStreamInternal !== null;
      const hasRemoteCameras = cameraStates.size > 0;

      if (hasLocalStream || hasRemoteCameras) {
        this.reconcilePeers(participants);
      }
    });

    this.destroyRef.onDestroy(() => this.stop());
  }

  async start(): Promise<void> {
    try {
      this.mediaError.set(null);
      if (!navigator.mediaDevices?.getUserMedia) {
        throw new Error(
          'Camera/microphone unavailable. Please use HTTPS or localhost.',
        );
      }
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

      // Broadcast camera state via WebSocket
      this.ws.sendCameraState(true);

      // Initiate connections to all existing participants
      const participants = this.ws.participants();
      this.reconcilePeers(participants);
    } catch (err) {
      const message = err instanceof DOMException
        ? `Camera/microphone access denied: ${err.message}`
        : err instanceof Error
          ? err.message
          : 'Failed to access media devices';
      this.mediaError.set(message);
      throw err;
    }
  }

  stop(): void {
    this.ws.sendCameraState(false);

    for (const [id, pc] of this.peerConnections) {
      pc.close();
    }
    this.peerConnections.clear();
    this.peerNicknames.clear();
    this.pendingIceCandidates.clear();
    this.remoteStreams.set([]);

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
      this.ws.sendCameraState(videoTrack.enabled);
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
    const myConnectionId = this._myConnectionId;
    if (!myConnectionId) return;

    const otherParticipants = participants
      .filter(p => p.connectionId !== myConnectionId)
      .slice(0, MAX_WEBRTC_PARTICIPANTS - 1);

    const activeIds = new Set(otherParticipants.map(p => p.connectionId));
    const weHaveCamera = this.localStreamInternal !== null;

    // Remove stale connections
    for (const [id, pc] of this.peerConnections) {
      if (!activeIds.has(id)) {
        pc.close();
        this.peerConnections.delete(id);
        this.peerNicknames.delete(id);
      }
    }

    // Only create new connections when we have a camera.
    // Peers with cameras will initiate to us when they see us in the participants list.
    for (const participant of otherParticipants) {
      this.peerNicknames.set(participant.connectionId, participant.nickname);

      if (!this.peerConnections.has(participant.connectionId)) {
        if (weHaveCamera) {
          this.createPeerConnection(participant.connectionId, true);
        }
      } else if (weHaveCamera) {
        // Add local tracks to existing connections that don't have them yet
        const pc = this.peerConnections.get(participant.connectionId)!;
        const hasLocalTracks = pc.getSenders().some(s => s.track !== null);
        if (!hasLocalTracks && this.localStreamInternal) {
          for (const track of this.localStreamInternal.getTracks()) {
            pc.addTrack(track, this.localStreamInternal);
          }
          this.createAndSendOffer(pc, participant.connectionId);
        }
      }
    }

    this.updateRemoteStreams();
  }

  private createPeerConnection(remoteConnectionId: string, initiator: boolean): void {
    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
    this.peerConnections.set(remoteConnectionId, pc);

    if (this.localStreamInternal) {
      for (const track of this.localStreamInternal.getTracks()) {
        pc.addTrack(track, this.localStreamInternal);
      }
    }

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

    pc.ontrack = () => {
      this.zone.run(() => this.updateRemoteStreams());
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
        let pc = this.peerConnections.get(fromId);
        // Handle glare: both sides sent offers simultaneously
        if (pc && pc.signalingState === 'have-local-offer') {
          if (this._myConnectionId! < fromId) return;
          pc.close();
          this.peerConnections.delete(fromId);
          pc = undefined;
        }
        // Resolve nickname from participants list
        const participants = this.ws.participants();
        const sender = participants.find(p => p.connectionId === fromId);
        if (sender) {
          this.peerNicknames.set(fromId, sender.nickname);
        }
        if (!pc) {
          this.createPeerConnection(fromId, false);
          pc = this.peerConnections.get(fromId);
        }
        if (!pc || !signal.sdp) return;

        try {
          await pc.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp: signal.sdp }));
          // Flush any ICE candidates that arrived before the offer
          await this.flushIceCandidates(fromId, pc);
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
          // Flush any ICE candidates that arrived before the answer
          await this.flushIceCandidates(fromId, pc);
        } catch (err) {
          console.error('Failed to handle WebRTC answer:', err);
        }
        break;
      }
      case 'ice-candidate': {
        const pc = this.peerConnections.get(fromId);
        if (!signal.candidate) return;
        const candidateInit: RTCIceCandidateInit = {
          candidate: signal.candidate,
          sdpMid: signal.sdpMid ?? undefined,
          sdpMLineIndex: signal.sdpMLineIndex ?? undefined,
        };
        // Buffer if PC doesn't exist yet or has no remote description
        if (!pc || !pc.remoteDescription) {
          const pending = this.pendingIceCandidates.get(fromId) ?? [];
          pending.push(candidateInit);
          this.pendingIceCandidates.set(fromId, pending);
          return;
        }
        try {
          await pc.addIceCandidate(new RTCIceCandidate(candidateInit));
        } catch (err) {
          console.error('Failed to add ICE candidate:', err);
        }
        break;
      }
    }
  }

  private async flushIceCandidates(peerId: string, pc: RTCPeerConnection): Promise<void> {
    const pending = this.pendingIceCandidates.get(peerId);
    if (!pending || pending.length === 0) return;
    this.pendingIceCandidates.delete(peerId);
    for (const candidate of pending) {
      try {
        await pc.addIceCandidate(new RTCIceCandidate(candidate));
      } catch (err) {
        console.error('Failed to add buffered ICE candidate:', err);
      }
    }
  }

  private updateRemoteStreams(): void {
    const cameraStates = this.ws.peerCameraStates();
    const peers: RemotePeer[] = [];
    for (const [connectionId, pc] of this.peerConnections) {
      if (!cameraStates.has(connectionId)) continue;
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
