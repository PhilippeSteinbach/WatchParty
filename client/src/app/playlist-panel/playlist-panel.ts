import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CdkDropList, CdkDrag, CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { PlaylistService } from '../services/playlist.service';
import { WebSocketService } from '../services/websocket.service';
import { PlaylistItem } from '../models/room.model';

@Component({
  selector: 'app-playlist-panel',
  standalone: true,
  imports: [FormsModule, CdkDropList, CdkDrag],
  templateUrl: './playlist-panel.html',
  styleUrl: './playlist-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlaylistPanelComponent {
  private readonly playlist = inject(PlaylistService);
  private readonly ws = inject(WebSocketService);

  readonly items = this.playlist.items;
  readonly roomState = this.ws.roomState;
  readonly urlInput = signal('');

  addToQueue(): void {
    const url = this.urlInput().trim();
    if (!url) return;
    this.playlist.addToQueue(url);
    this.urlInput.set('');
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
    const items = this.items();
    const item = items[event.previousIndex];
    moveItemInArray(items, event.previousIndex, event.currentIndex);
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
}
