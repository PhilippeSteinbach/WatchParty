import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  output,
  effect,
  signal,
  viewChild,
  OnDestroy,
  AfterViewInit,
  NgZone,
  inject,
} from '@angular/core';
import { PlayerState, VideoRecommendation } from '../models/room.model';

@Component({
  selector: 'app-youtube-player',
  standalone: true,
  templateUrl: './youtube-player.html',
  styleUrl: './youtube-player.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YoutubePlayerComponent implements AfterViewInit, OnDestroy {
  private readonly zone = inject(NgZone);

  readonly videoUrl = input('');
  readonly isPlaying = input(false);
  readonly currentTime = input(0);
  readonly playbackRate = input(1.0);
  readonly recommendations = input<VideoRecommendation[]>([]);

  readonly playerEvent = output<PlayerState>();
  readonly overlayClick = output<void>();
  readonly playRecommendation = output<VideoRecommendation>();
  readonly queueRecommendation = output<VideoRecommendation>();

  readonly playerEl = viewChild<ElementRef<HTMLDivElement>>('playerContainer');

  private player: YT.Player | null = null;
  private apiLoaded = false;
  private lastVideoId = '';

  ngAfterViewInit(): void {
    this.loadYouTubeApi();
  }

  ngOnDestroy(): void {
    this.player?.destroy();
  }

  private wasPlaying = false;
  private readonly _showOverlayContent = signal(false);
  private readonly _videoEverStarted = signal(false);
  readonly showOverlayContent = this._showOverlayContent.asReadonly();
  readonly videoEverStarted = this._videoEverStarted.asReadonly();

  constructor() {
    effect(() => {
      const url = this.videoUrl();
      const videoId = this.extractVideoId(url);
      if (videoId && videoId !== this.lastVideoId && this.player) {
        this.lastVideoId = videoId;
        this._showOverlayContent.set(false);
        this._videoEverStarted.set(this.isPlaying());
        this.wasPlaying = this.isPlaying();
        if (this.isPlaying()) {
          this.player.loadVideoById({ videoId, startSeconds: this.currentTime() });
        } else {
          this.player.cueVideoById({ videoId, startSeconds: this.currentTime() });
        }
      }
    });

    effect(() => {
      const playing = this.isPlaying();
      if (playing) {
        this._videoEverStarted.set(true);
        this._showOverlayContent.set(false);
      } else if (this.wasPlaying) {
        this._showOverlayContent.set(true);
      }
      this.wasPlaying = playing;
      if (!this.player) return;
      if (playing) {
        this.player.playVideo();
      } else {
        this.player.pauseVideo();
      }
    });

    effect(() => {
      const time = this.currentTime();
      if (!this.player) return;
      const diff = Math.abs((this.player.getCurrentTime?.() ?? 0) - time);
      if (diff > 2) {
        this.player.seekTo(time, true);
      }
    });

    effect(() => {
      const rate = this.playbackRate();
      if (!this.player) return;
      this.player.setPlaybackRate(rate);
    });
  }

  getCurrentTime(): number {
    return this.player?.getCurrentTime?.() ?? 0;
  }

  getDuration(): number {
    return this.player?.getDuration?.() ?? 0;
  }

  seekTo(time: number): void {
    this.player?.seekTo(time, true);
  }

  setVolume(volume: number): void {
    this.player?.setVolume(volume);
  }

  onOverlayClick(): void {
    this.overlayClick.emit();
  }

  onPlayRec(rec: VideoRecommendation): void {
    this.playRecommendation.emit(rec);
  }

  onQueueRec(rec: VideoRecommendation): void {
    this.queueRecommendation.emit(rec);
  }

  formatDuration(seconds: number): string {
    if (!seconds) return '';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    return `${m}:${String(s).padStart(2, '0')}`;
  }

  private loadYouTubeApi(): void {
    if ((window as unknown as Record<string, unknown>)['YT']) {
      this.apiLoaded = true;
      this.initPlayer();
      return;
    }

    const tag = document.createElement('script');
    tag.src = 'https://www.youtube.com/iframe_api';
    document.head.appendChild(tag);

    (window as unknown as Record<string, () => void>)['onYouTubeIframeAPIReady'] = () => {
      this.zone.run(() => {
        this.apiLoaded = true;
        this.initPlayer();
      });
    };
  }

  private initPlayer(): void {
    const container = this.playerEl()?.nativeElement;
    if (!container) return;

    const videoId = this.extractVideoId(this.videoUrl()) || '';
    this.lastVideoId = videoId;

    this.player = new YT.Player(container, {
      width: '100%',
      height: '100%',
      videoId,
      playerVars: { autoplay: 0, controls: 0, disablekb: 1, modestbranding: 1, rel: 0, iv_load_policy: 3 },
      events: {
        onReady: () => {
          this.zone.run(() => {
            const time = this.currentTime();
            if (time > 0) {
              this.player!.seekTo(time, true);
            }
            if (this.isPlaying()) {
              this._videoEverStarted.set(true);
              this._showOverlayContent.set(false);
              this.player!.playVideo();
            }
          });
        },
        onStateChange: (event: YT.OnStateChangeEvent) => {
          this.zone.run(() => this.onPlayerStateChange(event));
        },
      },
    });
  }

  private onPlayerStateChange(event: YT.OnStateChangeEvent): void {
    if (event.data === YT.PlayerState.ENDED) {
      this._showOverlayContent.set(true);
      this.playerEvent.emit({
        action: 'ENDED',
        currentTimeSeconds: this.player?.getCurrentTime?.() ?? 0,
        isPlaying: false,
      });
    } else if (event.data === YT.PlayerState.PLAYING && !this.isPlaying()) {
      // YouTube auto-played but our state says paused — force pause
      this.player?.pauseVideo();
    }
  }

  extractVideoId(url: string): string {
    if (!url) return '';
    const shortMatch = url.match(/youtu\.be\/([^?&]+)/);
    if (shortMatch) return shortMatch[1];
    const longMatch = url.match(/[?&]v=([^&]+)/);
    return longMatch ? longMatch[1] : '';
  }
}
