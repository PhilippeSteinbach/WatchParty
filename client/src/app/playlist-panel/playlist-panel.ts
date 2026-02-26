import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { CdkDropList, CdkDrag, CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { PlaylistService } from '../services/playlist.service';
import { WebSocketService } from '../services/websocket.service';
import { PlaylistItem } from '../models/room.model';
import { ConfirmDialogComponent } from '../confirm-dialog/confirm-dialog';
import { extractPlaylistId } from '../utils/youtube.utils';

@Component({
  selector: 'app-playlist-panel',
  standalone: true,
  imports: [FormsModule, CdkDropList, CdkDrag, ConfirmDialogComponent],
  templateUrl: './playlist-panel.html',
  styleUrl: './playlist-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlaylistPanelComponent {
  private readonly playlist = inject(PlaylistService);
  private readonly ws = inject(WebSocketService);
  private readonly http = inject(HttpClient);

  readonly items = this.playlist.items;
  readonly roomState = this.ws.roomState;
  readonly urlInput = signal('');
  readonly showConfirmDialog = signal(false);
  readonly confirmMessage = signal('');
  private pendingPlaylistUrls: string[] = [];

  addToQueue(): void {
    const url = this.urlInput().trim();
    if (!url) return;

    const playlistId = extractPlaylistId(url);
    if (playlistId) {
      this.handlePlaylistUrl(playlistId);
      return;
    }

    this.playlist.addToQueue(url);
    this.urlInput.set('');
  }

  private handlePlaylistUrl(playlistId: string): void {
    this.http.get<any>(`/api/videos/playlist/${playlistId}`).subscribe({
      next: (info) => {
        this.pendingPlaylistUrls = info.items.map((item: any) => item.videoUrl);
        this.confirmMessage.set(`Add ${info.videoCount} videos from "${info.title}" to the playlist?`);
        this.showConfirmDialog.set(true);
      },
      error: () => {
        this.playlist.addToQueue(this.urlInput().trim());
        this.urlInput.set('');
      }
    });
  }

  onConfirmPlaylist(): void {
    this.playlist.addBulkToQueue(this.pendingPlaylistUrls);
    this.pendingPlaylistUrls = [];
    this.showConfirmDialog.set(false);
    this.urlInput.set('');
  }

  onCancelPlaylist(): void {
    this.pendingPlaylistUrls = [];
    this.showConfirmDialog.set(false);
  }

  playNow(): void {
    const url = this.urlInput().trim();
    if (!url) return;
    this.playlist.playNow(url);
    this.urlInput.set('');
  }

  playItem(item: PlaylistItem): void {
    this.playlist.playNow(item.videoUrl);
  }

  removeItem(itemId: string): void {
    this.playlist.remove(itemId);
  }

  skipToNext(): void {
    this.playlist.skipToNext();
  }

  onDrop(event: CdkDragDrop<PlaylistItem[]>): void {
    if (event.previousIndex === event.currentIndex) return;
    const items = [...this.items()];
    const item = items[event.previousIndex];
    moveItemInArray(items, event.previousIndex, event.currentIndex);
    this.ws.playlistItems.set(items);
    this.playlist.reorder(item.id, event.currentIndex + 1);
  }

  isCurrentlyPlaying(item: PlaylistItem): boolean {
    return this.roomState()?.currentVideoUrl === item.videoUrl;
  }

  formatDuration(seconds: number): string {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  displayTitle(item: PlaylistItem): string {
    return item.title ?? item.videoUrl;
  }

  thumbnailFor(item: PlaylistItem): string | null {
    if (item.thumbnailUrl) return item.thumbnailUrl;
    const id = this.extractVideoId(item.videoUrl);
    return id ? `https://img.youtube.com/vi/${id}/mqdefault.jpg` : null;
  }

  private extractVideoId(url: string): string | null {
    const short = url.match(/youtu\.be\/([^?&]+)/);
    if (short) return short[1];
    const long = url.match(/[?&]v=([^&]+)/);
    return long ? long[1] : null;
  }
}
