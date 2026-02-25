import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
  effect,
  viewChild,
  OnDestroy,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WebSocketService } from '../services/websocket.service';
import { VideoRecommendationService } from '../services/video-recommendation.service';
import { YoutubePlayerComponent } from '../youtube-player/youtube-player';
import { VideoControlsComponent } from '../video-controls/video-controls';
import { ParticipantListComponent } from '../participant-list/participant-list';
import { ChatPanelComponent } from '../chat-panel/chat-panel';
import { PlaylistPanelComponent } from '../playlist-panel/playlist-panel';
import { PlayerState, VideoRecommendation } from '../models/room.model';

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
  private readonly recService = inject(VideoRecommendationService);

  readonly roomCode = input.required<string>();
  readonly roomState = this.ws.roomState;
  readonly participants = this.ws.participants;

  readonly videoUrlInput = signal('');
  readonly linkCopied = signal(false);
  readonly activeTab = signal<'chat' | 'playlist'>('chat');
  readonly sidebarCollapsed = signal(false);
  private readonly lastSeenChatCount = signal(0);
  readonly unreadChatCount = computed(() =>
    Math.max(0, this.ws.chatMessages().length - this.lastSeenChatCount())
  );
  readonly playbackRate = signal(1.0);
  readonly polledTime = signal(0);
  readonly polledDuration = signal(0);
  readonly recommendations = signal<VideoRecommendation[]>([]);

  readonly playerRef = viewChild(YoutubePlayerComponent);

  private positionReportInterval: ReturnType<typeof setInterval> | null = null;
  private timePollingInterval: ReturnType<typeof setInterval> | null = null;
  private lastRecVideoId = '';

  constructor() {
    // Start periodic position reporting when connected and playing
    effect(() => {
      const state = this.ws.roomState();
      if (!state) return;
      // Clear local override once server state arrives
      this.localIsPlaying.set(null);
      if (state.isPlaying) {
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

    // Reset unread count when chat tab is active
    effect(() => {
      if (this.activeTab() === 'chat') {
        this.lastSeenChatCount.set(this.ws.chatMessages().length);
      }
    });

    // Fetch recommendations when video changes
    effect(() => {
      const url = this.roomState()?.currentVideoUrl ?? '';
      const videoId = this.playerRef()?.extractVideoId(url) ?? '';
      if (videoId && videoId !== this.lastRecVideoId) {
        this.lastRecVideoId = videoId;
        this.recService.getRecommendations(videoId).subscribe(recs => this.recommendations.set(recs));
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

  // Local override for immediate UI feedback; null = use server state
  private readonly localIsPlaying = signal<boolean | null>(null);

  get currentVideoUrl(): string {
    return this.roomState()?.currentVideoUrl ?? '';
  }

  get currentTime(): number {
    return this.roomState()?.currentTimeSeconds ?? 0;
  }

  get isPlaying(): boolean {
    return this.localIsPlaying() ?? this.roomState()?.isPlaying ?? false;
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

    this.localIsPlaying.set(newIsPlaying);

    if (newIsPlaying) {
      player?.seekTo(time);
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

  onPlayRecommendation(rec: VideoRecommendation): void {
    this.localIsPlaying.set(true);
    this.ws.sendPlayerAction({
      action: 'CHANGE_VIDEO',
      videoUrl: rec.videoUrl,
      currentTimeSeconds: 0,
      isPlaying: false,
    });
    // Server forces isPlaying=false on CHANGE_VIDEO, so follow up with PLAY
    setTimeout(() => {
      this.ws.sendPlayerAction({
        action: 'PLAY',
        currentTimeSeconds: 0,
        isPlaying: true,
      });
    }, 300);
  }

  onQueueRecommendation(rec: VideoRecommendation): void {
    this.ws.addToPlaylist(rec.videoUrl);
  }
}
