import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CdkDropList, CdkDrag, CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { LucideAngularModule, Play, GripVertical, X, SkipForward, ListOrdered, Shuffle, Search } from 'lucide-angular';
import { PlaylistService } from '../services/playlist.service';
import { WebSocketService } from '../services/websocket.service';
import { PlaylistItem } from '../models/room.model';

@Component({
  selector: 'app-playlist-panel',
  standalone: true,
  imports: [FormsModule, CdkDropList, CdkDrag, LucideAngularModule],
  templateUrl: './playlist-panel.html',
  styleUrl: './playlist-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlaylistPanelComponent {
  readonly Play = Play;
  readonly GripVertical = GripVertical;
  readonly X = X;
  readonly SkipForward = SkipForward;
  readonly ListOrdered = ListOrdered;
  readonly Shuffle = Shuffle;
  readonly Search = Search;

  private readonly playlist = inject(PlaylistService);
  private readonly ws = inject(WebSocketService);

  readonly items = this.playlist.items;
  readonly roomState = this.ws.roomState;
  readonly playbackMode = this.playlist.playbackMode;
  readonly searchOpen = signal(false);
  readonly searchQuery = signal('');

  readonly filteredItems = computed(() => {
    const query = this.searchQuery().toLowerCase().trim();
    if (!query) return this.items();
    return this.items().filter(item =>
      this.displayTitle(item).toLowerCase().includes(query)
    );
  });

  toggleSearch(): void {
    const open = !this.searchOpen();
    this.searchOpen.set(open);
    if (!open) this.searchQuery.set('');
  }

  setMode(mode: 'ORDERED' | 'SHUFFLE'): void {
    this.playlist.setPlaybackMode(mode);
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
