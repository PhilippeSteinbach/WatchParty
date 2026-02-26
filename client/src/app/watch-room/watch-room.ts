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
  HostListener,
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { toObservable, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { switchMap, map, distinctUntilChanged, pairwise } from 'rxjs/operators';
import { of } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { WebSocketService } from '../services/websocket.service';
import { WebRtcService } from '../services/webrtc.service';
import { VideoRecommendationService } from '../services/video-recommendation.service';
import { YoutubePlayerComponent } from '../youtube-player/youtube-player';
import { VideoControlsComponent } from '../video-controls/video-controls';
import { ParticipantListComponent } from '../participant-list/participant-list';
import { ChatPanelComponent } from '../chat-panel/chat-panel';
import { PlaylistPanelComponent } from '../playlist-panel/playlist-panel';
import { VideoGridComponent } from '../video-grid/video-grid';
import { MediaControlsComponent } from '../media-controls/media-controls';
import { ConfirmDialogComponent } from '../confirm-dialog/confirm-dialog';
import { PlaylistItem, PlayerState, VideoRecommendation } from '../models/room.model';
import { extractPlaylistId } from '../utils/youtube.utils';

@Component({
  selector: 'app-watch-room',
  standalone: true,
  imports: [FormsModule, YoutubePlayerComponent, VideoControlsComponent, ParticipantListComponent, ChatPanelComponent, PlaylistPanelComponent, VideoGridComponent, MediaControlsComponent, ConfirmDialogComponent],
  templateUrl: './watch-room.html',
  styleUrl: './watch-room.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WatchRoomComponent implements OnDestroy {
  private readonly ws = inject(WebSocketService);
  readonly webRtc = inject(WebRtcService);
  private readonly recService = inject(VideoRecommendationService);
  private readonly http = inject(HttpClient);

  readonly roomCode = input.required<string>();
  readonly roomState = this.ws.roomState;
  readonly participants = this.ws.participants;

  readonly videoUrlInput = signal('');
  readonly linkCopied = signal(false);
  readonly activeTab = signal<'playlist' | 'chat'>('playlist');
  readonly sidebarCollapsed = signal(false);
  private readonly lastSeenChatCount = signal(0);
  readonly unreadChatCount = computed(() =>
    Math.max(0, this.ws.chatMessages().length - this.lastSeenChatCount())
  );
  readonly playbackRate = signal(1.0);
  readonly polledTime = signal(0);
  readonly polledDuration = signal(0);
  readonly recommendations = signal<VideoRecommendation[]>([]);
  readonly notifications = signal<string[]>([]);
  readonly showPlaylistConfirm = signal(false);
  readonly playlistConfirmMessage = signal('');
  private pendingPlaylistUrls: string[] = [];

  /** YouTube API recommendations for the currently playing video */
  readonly displayRecommendations = computed<VideoRecommendation[]>(() => this.recommendations());

  readonly playerRef = viewChild(YoutubePlayerComponent);

  private positionReportInterval: ReturnType<typeof setInterval> | null = null;
  private timePollingInterval: ReturnType<typeof setInterval> | null = null;

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

    // Fetch recommendations whenever the current video changes
    toObservable(this.ws.roomState).pipe(
      map(state => this.extractYouTubeId(state?.currentVideoUrl ?? '')),
      distinctUntilChanged(),
      switchMap(videoId => videoId ? this.recService.getRecommendations(videoId) : of([])),
      takeUntilDestroyed()
    ).subscribe(recs => this.recommendations.set(recs));

    // Poll player time for custom controls (every 250ms)
    this.timePollingInterval = setInterval(() => {
      const player = this.playerRef();
      if (player) {
        this.polledTime.set(player.getCurrentTime());
        this.polledDuration.set(player.getDuration());
      }
    }, 250);

    // Track participant join/leave for toast notifications
    toObservable(this.ws.participants).pipe(
      pairwise(),
      takeUntilDestroyed()
    ).subscribe(([prev, curr]) => {
      const prevIds = new Set(prev.map(p => p.id));
      const currIds = new Set(curr.map(p => p.id));
      for (const p of curr) {
        if (!prevIds.has(p.id)) {
          this.addNotification(`${p.nickname} joined the room`);
        }
      }
      for (const p of prev) {
        if (!currIds.has(p.id)) {
          this.addNotification(`${p.nickname} left the room`);
        }
      }
    });
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    // Ignore when typing in input/textarea
    const target = event.target as HTMLElement;
    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') return;

    switch (event.code) {
      case 'Space':
        event.preventDefault();
        this.onPlayPause();
        break;
      case 'KeyM':
        if (this.webRtc.isActive()) {
          this.webRtc.toggleMic();
        }
        break;
      case 'KeyF':
        this.toggleFullscreen();
        break;
    }
  }

  private toggleFullscreen(): void {
    if (document.fullscreenElement) {
      document.exitFullscreen();
    } else {
      document.documentElement.requestFullscreen();
    }
  }

  ngOnDestroy(): void {
    this.stopPositionReporting();
    if (this.timePollingInterval) {
      clearInterval(this.timePollingInterval);
    }
    this.webRtc.stop();
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

  addVideo(): void {
    const url = this.videoUrlInput().trim();
    if (!url) return;

    const playlistId = extractPlaylistId(url);
    if (playlistId) {
      this.handlePlaylistImport(playlistId);
      return;
    }

    this.ws.addToPlaylist(url);
    this.videoUrlInput.set('');
  }

  private handlePlaylistImport(playlistId: string): void {
    this.http.get<any>(`/api/videos/playlist/${playlistId}`).subscribe({
      next: (info) => {
        this.pendingPlaylistUrls = info.items.map((item: any) => item.videoUrl);
        this.playlistConfirmMessage.set(`Add ${info.videoCount} videos from "${info.title}" to the playlist?`);
        this.showPlaylistConfirm.set(true);
      },
      error: () => {
        this.ws.addToPlaylist(this.videoUrlInput().trim());
        this.videoUrlInput.set('');
      }
    });
  }

  onConfirmPlaylistImport(): void {
    this.ws.addBulkToPlaylist(this.pendingPlaylistUrls);
    this.pendingPlaylistUrls = [];
    this.showPlaylistConfirm.set(false);
    this.videoUrlInput.set('');
  }

  onCancelPlaylistImport(): void {
    this.pendingPlaylistUrls = [];
    this.showPlaylistConfirm.set(false);
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

  async onStartMedia(): Promise<void> {
    try {
      await this.webRtc.start();
    } catch {
      // Error is already stored in webRtc.mediaError
    }
  }

  onStopMedia(): void {
    this.webRtc.stop();
  }

  onToggleCamera(): void {
    this.webRtc.toggleCamera();
  }

  onToggleMic(): void {
    this.webRtc.toggleMic();
  }

  addNotification(message: string): void {
    this.notifications.update(n => [...n, message]);
    setTimeout(() => {
      this.notifications.update(n => n.slice(1));
    }, 4000);
  }

  private extractYouTubeId(url: string): string {
    if (!url) return '';
    const short = url.match(/youtu\.be\/([^?&]+)/);
    if (short) return short[1];
    const long = url.match(/[?&]v=([^&]+)/);
    return long ? long[1] : '';
  }
}
