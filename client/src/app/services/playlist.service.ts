import { Injectable, inject, Signal } from '@angular/core';
import { WebSocketService } from './websocket.service';
import { PlaylistItem } from '../models/room.model';

@Injectable({ providedIn: 'root' })
export class PlaylistService {
  private readonly ws = inject(WebSocketService);

  readonly items: Signal<PlaylistItem[]> = this.ws.playlistItems;

  addToQueue(videoUrl: string): void {
    this.ws.addToPlaylist(videoUrl);
  }

  playNow(videoUrl: string): void {
    this.ws.playNow(videoUrl);
  }

  remove(itemId: string): void {
    this.ws.removeFromPlaylist(itemId);
  }

  skipToNext(): void {
    this.ws.playNext();
  }

  reorder(itemId: string, newPosition: number): void {
    this.ws.reorderPlaylist(itemId, newPosition);
  }
}
