import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WebSocketService } from '../services/websocket.service';
import { YoutubePlayerComponent } from '../youtube-player/youtube-player';
import { ParticipantListComponent } from '../participant-list/participant-list';
import { ChatPanelComponent } from '../chat-panel/chat-panel';
import { PlaylistPanelComponent } from '../playlist-panel/playlist-panel';
import { PlayerState } from '../models/room.model';

@Component({
  selector: 'app-watch-room',
  standalone: true,
  imports: [FormsModule, YoutubePlayerComponent, ParticipantListComponent, ChatPanelComponent, PlaylistPanelComponent],
  templateUrl: './watch-room.html',
  styleUrl: './watch-room.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WatchRoomComponent {
  private readonly ws = inject(WebSocketService);

  readonly roomCode = input.required<string>();
  readonly roomState = this.ws.roomState;
  readonly participants = this.ws.participants;

  readonly videoUrlInput = signal('');
  readonly linkCopied = signal(false);
  readonly activeTab = signal<'chat' | 'playlist'>('chat');

  get currentVideoUrl(): string {
    return this.roomState()?.currentVideoUrl ?? '';
  }

  get currentTime(): number {
    return this.roomState()?.currentTimeSeconds ?? 0;
  }

  get isPlaying(): boolean {
    return this.roomState()?.isPlaying ?? false;
  }

  changeVideo(): void {
    const url = this.videoUrlInput().trim();
    if (!url) return;

    this.ws.sendPlayerAction({
      action: 'CHANGE_VIDEO',
      videoUrl: url,
      currentTimeSeconds: 0,
      isPlaying: false,
    });
  }

  onPlayerEvent(action: PlayerState): void {
    this.ws.sendPlayerAction(action);
  }

  copyLink(): void {
    const url = `${window.location.origin}/room/${this.roomCode()}`;
    navigator.clipboard.writeText(url);
    this.linkCopied.set(true);
    setTimeout(() => this.linkCopied.set(false), 2000);
  }

  requestSync(): void {
    this.ws.requestSync();
  }
}
