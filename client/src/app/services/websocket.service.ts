import { Injectable, signal, computed, inject, NgZone } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { ChatMessage, Participant, PlayerState, PlaylistItem, RoomState, SyncCorrection, WebRtcSignalEnvelope } from '../models/room.model';

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private readonly zone = inject(NgZone);
  private client: Client | null = null;
  private roomCode = '';

  readonly roomState = signal<RoomState | null>(null);
  readonly connected = signal(false);
  readonly participants = computed(() => this.roomState()?.participants ?? []);
  readonly chatMessages = signal<ChatMessage[]>([]);
  readonly playlistItems = signal<PlaylistItem[]>([]);
  readonly syncCorrection = signal<SyncCorrection | null>(null);
  readonly webRtcSignal = signal<WebRtcSignalEnvelope[]>([]);
  readonly myConnectionId = signal<string | null>(null);
  readonly peerCameraStates = signal<Map<string, boolean>>(new Map());

  connect(roomCode: string, nickname: string): void {
    this.roomCode = roomCode;

    this.client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      onConnect: () => {
        this.connected.set(true);

        this.client!.subscribe(`/topic/room.${roomCode}`, (message: IMessage) => {
          this.zone.run(() => {
            const body = JSON.parse(message.body);
            if (body.participants) {
              this.roomState.set(body as RoomState);
              // Clean up stale camera states for participants who left
              const activeIds = new Set((body as RoomState).participants.map((p: Participant) => p.connectionId));
              this.peerCameraStates.update(map => {
                let changed = false;
                const next = new Map(map);
                for (const id of next.keys()) {
                  if (!activeIds.has(id)) {
                    next.delete(id);
                    changed = true;
                  }
                }
                return changed ? next : map;
              });
            } else {
              const current = this.roomState();
              if (current) {
                const ps = body as PlayerState;
                this.roomState.set({
                  ...current,
                  currentVideoUrl: ps.videoUrl ?? current.currentVideoUrl,
                  currentTimeSeconds: ps.currentTimeSeconds,
                  isPlaying: ps.isPlaying,
                });
              }
            }
          });
        });

        this.client!.subscribe(`/topic/room.${roomCode}.chat`, (message: IMessage) => {
          this.zone.run(() => {
            const msg = JSON.parse(message.body) as ChatMessage;
            this.chatMessages.update(messages => {
              const idx = messages.findIndex(m => m.id === msg.id);
              if (idx >= 0) {
                const updated = [...messages];
                updated[idx] = msg;
                return updated;
              }
              return [...messages, msg];
            });
          });
        });

        this.client!.subscribe('/user/queue/chat.history', (message: IMessage) => {
          this.zone.run(() => {
            this.chatMessages.set(JSON.parse(message.body) as ChatMessage[]);
          });
        });

        this.client!.subscribe('/user/queue/playlist.history', (message: IMessage) => {
          this.zone.run(() => {
            const body = JSON.parse(message.body) as { items: PlaylistItem[] };
            this.playlistItems.set(body.items);
          });
        });

        this.client!.subscribe(`/topic/room.${roomCode}.playlist`, (message: IMessage) => {
          this.zone.run(() => {
            const body = JSON.parse(message.body) as { items: PlaylistItem[] };
            this.playlistItems.set(body.items);
          });
        });

        this.client!.subscribe('/user/queue/sync.correction', (message: IMessage) => {
          this.zone.run(() => {
            this.syncCorrection.set(JSON.parse(message.body) as SyncCorrection);
          });
        });

        this.client!.subscribe('/user/queue/webrtc.signal', (message: IMessage) => {
          this.zone.run(() => {
            const sig = JSON.parse(message.body) as WebRtcSignalEnvelope;
            this.webRtcSignal.update(q => [...q, sig]);
          });
        });

        this.client!.subscribe(`/topic/room.${roomCode}.camera-state`, (message: IMessage) => {
          this.zone.run(() => {
            const body = JSON.parse(message.body) as { connectionId: string; enabled: boolean };
            if (body.connectionId === this.myConnectionId()) return;
            this.peerCameraStates.update(map => {
              const next = new Map(map);
              if (body.enabled) {
                next.set(body.connectionId, true);
              } else {
                next.delete(body.connectionId);
              }
              return next;
            });
          });
        });

        this.client!.subscribe('/user/queue/session.info', (message: IMessage) => {
          this.zone.run(() => {
            const body = JSON.parse(message.body) as { connectionId: string };
            this.myConnectionId.set(body.connectionId);
          });
        });

        this.client!.publish({
          destination: '/app/room.join',
          body: JSON.stringify({ roomCode, nickname }),
        });
      },
      onDisconnect: () => this.connected.set(false),
      onStompError: () => this.connected.set(false),
    });

    this.client.activate();
  }

  disconnect(): void {
    if (this.client?.active) {
      this.client.publish({ destination: '/app/room.leave', body: '' });
      this.client.deactivate();
    }
    this.connected.set(false);
    this.roomState.set(null);
    this.chatMessages.set([]);
    this.playlistItems.set([]);
    this.syncCorrection.set(null);
    this.webRtcSignal.set([]);
    this.myConnectionId.set(null);
    this.peerCameraStates.set(new Map());
  }

  sendPlayerAction(action: PlayerState): void {
    if (this.client?.active) {
      this.client.publish({
        destination: '/app/room.player',
        body: JSON.stringify(action),
      });
    }
  }

  requestSync(): void {
    if (this.client?.active) {
      this.client.publish({ destination: '/app/room.sync', body: '' });
    }
  }

  sendChatMessage(content: string): void {
    if (this.client?.active) {
      this.client.publish({
        destination: '/app/room.chat',
        body: JSON.stringify({ content }),
      });
    }
  }

  addReaction(messageId: string, emoji: string): void {
    if (this.client?.active) {
      this.client.publish({
        destination: '/app/room.chat.reaction',
        body: JSON.stringify({ messageId, emoji }),
      });
    }
  }

  addToPlaylist(videoUrl: string): void {
    if (this.client?.active) {
      this.client.publish({
        destination: '/app/room.playlist.add',
        body: JSON.stringify({ videoUrl }),
      });
    }
  }

  addBulkToPlaylist(videoUrls: string[]): void {
    if (this.client?.active) {
      this.client.publish({
        destination: '/app/room.playlist.add-bulk',
        body: JSON.stringify({ videoUrls }),
      });
    }
  }

  playNow(videoUrl: string): void {
    if (this.client?.active) {
      this.client.publish({
        destination: '/app/room.playlist.playNow',
        body: JSON.stringify({ videoUrl }),
      });
    }
  }

  removeFromPlaylist(itemId: string): void {
    if (this.client?.active) {
      this.client.publish({
        destination: '/app/room.playlist.remove',
        body: JSON.stringify({ itemId }),
      });
    }
  }

  playNext(): void {
    if (this.client?.active) {
      this.client.publish({ destination: '/app/room.playlist.next', body: '' });
    }
  }

  setPlaybackMode(mode: 'ORDERED' | 'SHUFFLE'): void {
    if (this.client?.active) {
      this.client.publish({
        destination: '/app/room.playlist.mode',
        body: JSON.stringify({ mode }),
      });
    }
  }

  reorderPlaylist(itemId: string, newPosition: number): void {
    if (this.client?.active) {
      this.client.publish({
        destination: '/app/room.playlist.reorder',
        body: JSON.stringify({ itemId, newPosition }),
      });
    }
  }

  reportPosition(currentTimeSeconds: number): void {
    if (this.client?.active) {
      this.client.publish({
        destination: '/app/room.position.report',
        body: JSON.stringify({ currentTimeSeconds }),
      });
    }
  }

  sendWebRtcOffer(targetConnectionId: string, sdp: string): void {
    if (this.client?.active) {
      this.client.publish({
        destination: '/app/room.webrtc.offer',
        body: JSON.stringify({ targetConnectionId, sdp }),
      });
    }
  }

  sendWebRtcAnswer(targetConnectionId: string, sdp: string): void {
    if (this.client?.active) {
      this.client.publish({
        destination: '/app/room.webrtc.answer',
        body: JSON.stringify({ targetConnectionId, sdp }),
      });
    }
  }

  sendWebRtcIceCandidate(targetConnectionId: string, candidate: string, sdpMid: string | null, sdpMLineIndex: number | null): void {
    if (this.client?.active) {
      this.client.publish({
        destination: '/app/room.webrtc.ice',
        body: JSON.stringify({ targetConnectionId, candidate, sdpMid, sdpMLineIndex }),
      });
    }
  }

  sendCameraState(enabled: boolean): void {
    if (this.client?.active) {
      this.client.publish({
        destination: '/app/room.webrtc.camera-state',
        body: JSON.stringify({ enabled }),
      });
    }
  }
}
