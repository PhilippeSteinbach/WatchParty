import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WebSocketService } from '../services/websocket.service';
import { PlaylistItem } from '../models/room.model';

@Component({
  selector: 'app-playlist-panel',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './playlist-panel.html',
  styleUrl: './playlist-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlaylistPanelComponent {
  private readonly ws = inject(WebSocketService);

  readonly items = this.ws.playlistItems;
  readonly roomState = this.ws.roomState;
  readonly urlInput = signal('');

  addVideo(): void {
    const url = this.urlInput().trim();
    if (!url) return;
    this.ws.addToPlaylist(url);
    this.urlInput.set('');
  }

  removeItem(itemId: string): void {
    this.ws.removeFromPlaylist(itemId);
  }

  playNext(): void {
    this.ws.playNext();
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
