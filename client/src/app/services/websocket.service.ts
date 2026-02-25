import { Injectable, signal, computed } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Participant, PlayerState, RoomState } from '../models/room.model';

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private client: Client | null = null;
  private roomCode = '';

  readonly roomState = signal<RoomState | null>(null);
  readonly connected = signal(false);
  readonly participants = computed(() => this.roomState()?.participants ?? []);

  connect(roomCode: string, nickname: string): void {
    this.roomCode = roomCode;

    this.client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      onConnect: () => {
        this.connected.set(true);

        this.client!.subscribe(`/topic/room.${roomCode}`, (message: IMessage) => {
          const body = JSON.parse(message.body);
          if (body.participants) {
            this.roomState.set(body as RoomState);
          } else {
            // PlayerState — merge into existing room state
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
}
