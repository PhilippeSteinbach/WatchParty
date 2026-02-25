import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  signal,
  effect,
  viewChild,
  OnDestroy,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WebSocketService } from '../services/websocket.service';
import { YoutubePlayerComponent } from '../youtube-player/youtube-player';
import { VideoControlsComponent } from '../video-controls/video-controls';
import { ParticipantListComponent } from '../participant-list/participant-list';
import { ChatPanelComponent } from '../chat-panel/chat-panel';
import { PlaylistPanelComponent } from '../playlist-panel/playlist-panel';
import { PlayerState } from '../models/room.model';

@Component({
  selector: 'app-watch-room',
  standalone: true,
  imports: [FormsModule, YoutubePlayerComponent, VideoControlsComponent, ParticipantListComponent, ChatPanelComponent, PlaylistPanelComponent],
  templateUrl: './watch-room.html',
  styleUrl: './watch-room.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WatchRoomComponent implements OnDestroy {
  private readonly ws = inject(WebSocketService);

  readonly roomCode = input.required<string>();
  readonly roomState = this.ws.roomState;
  readonly participants = this.ws.participants;

  readonly videoUrlInput = signal('');
  readonly linkCopied = signal(false);
  readonly activeTab = signal<'chat' | 'playlist'>('chat');
  readonly playbackRate = signal(1.0);
  readonly polledTime = signal(0);
  readonly polledDuration = signal(0);

  readonly playerRef = viewChild(YoutubePlayerComponent);

  private positionReportInterval: ReturnType<typeof setInterval> | null = null;
  private timePollingInterval: ReturnType<typeof setInterval> | null = null;

  constructor() {
    // Start periodic position reporting when connected and playing
    effect(() => {
      const state = this.ws.roomState();
      if (state?.isPlaying) {
        this.startPositionReporting();
      } else {
        this.stopPositionReporting();
      }
    });

    // Handle sync corrections from server
    effect(() => {
      const correction = this.ws.syncCorrection();
      if (!correction) return;

      if (correction.correctionType === 'RATE_ADJUST') {
        this.playbackRate.set(correction.playbackRate);
      } else if (correction.correctionType === 'SEEK') {
        this.playbackRate.set(1.0);
        this.playerRef()?.seekTo(correction.targetTimeSeconds);
      } else if (correction.correctionType === 'RATE_RESET') {
        this.playbackRate.set(1.0);
      }
    });

    // Poll player time for custom controls (every 250ms)
    this.timePollingInterval = setInterval(() => {
      const player = this.playerRef();
      if (player) {
        this.polledTime.set(player.getCurrentTime());
        this.polledDuration.set(player.getDuration());
      }
    }, 250);
  }

  ngOnDestroy(): void {
    this.stopPositionReporting();
    if (this.timePollingInterval) {
      clearInterval(this.timePollingInterval);
    }
  }

  private startPositionReporting(): void {
    if (this.positionReportInterval) return;
    this.positionReportInterval = setInterval(() => {
      const player = this.playerRef();
      if (player) {
        this.ws.reportPosition(player.getCurrentTime());
      }
    }, 5000);
  }

  private stopPositionReporting(): void {
    if (this.positionReportInterval) {
      clearInterval(this.positionReportInterval);
      this.positionReportInterval = null;
    }
    this.playbackRate.set(1.0);
  }

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
    if (action.action === 'ENDED') {
      this.ws.playNext();
    }
  }

  onPlayPause(): void {
    const player = this.playerRef();
    const time = player?.getCurrentTime() ?? 0;
    const newIsPlaying = !this.isPlaying;

    // Apply locally immediately for responsive feel
    if (newIsPlaying) {
      player?.seekTo(time); // ensures position is synced before play
    }

    this.ws.sendPlayerAction({
      action: newIsPlaying ? 'PLAY' : 'PAUSE',
      currentTimeSeconds: time,
      isPlaying: newIsPlaying,
    });
  }

  onSeek(time: number): void {
    // Apply locally immediately
    this.playerRef()?.seekTo(time);

    this.ws.sendPlayerAction({
      action: 'SEEK',
      currentTimeSeconds: time,
      isPlaying: this.isPlaying,
    });
  }

  onVolumeChange(volume: number): void {
    this.playerRef()?.setVolume(volume);
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
