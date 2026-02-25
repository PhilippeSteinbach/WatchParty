import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  output,
  effect,
  viewChild,
  OnDestroy,
  AfterViewInit,
  NgZone,
  inject,
} from '@angular/core';
import { PlayerState } from '../models/room.model';

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
  readonly canControl = input(false);

  readonly playerEvent = output<PlayerState>();

  readonly playerEl = viewChild<ElementRef<HTMLDivElement>>('playerContainer');

  private player: YT.Player | null = null;
  private suppressEvents = false;
  private apiLoaded = false;
  private lastVideoId = '';

  ngAfterViewInit(): void {
    this.loadYouTubeApi();
  }

  ngOnDestroy(): void {
    this.player?.destroy();
  }

  constructor() {
    effect(() => {
      const url = this.videoUrl();
      const videoId = this.extractVideoId(url);
      if (videoId && videoId !== this.lastVideoId && this.player) {
        this.lastVideoId = videoId;
        this.suppressEvents = true;
        this.player.loadVideoById(videoId);
      }
    });

    effect(() => {
      const playing = this.isPlaying();
      if (!this.player) return;
      this.suppressEvents = true;
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
        this.suppressEvents = true;
        this.player.seekTo(time, true);
      }
    });
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
      playerVars: { autoplay: 0, controls: this.canControl() ? 1 : 0 },
      events: {
        onStateChange: (event: YT.OnStateChangeEvent) => {
          this.zone.run(() => this.onPlayerStateChange(event));
        },
      },
    });
  }

  private onPlayerStateChange(event: YT.OnStateChangeEvent): void {
    if (this.suppressEvents) {
      this.suppressEvents = false;
      return;
    }

    if (!this.canControl()) return;

    const time = this.player?.getCurrentTime?.() ?? 0;

    if (event.data === YT.PlayerState.PLAYING) {
      this.playerEvent.emit({
        action: 'PLAY',
        currentTimeSeconds: time,
        isPlaying: true,
      });
    } else if (event.data === YT.PlayerState.PAUSED) {
      this.playerEvent.emit({
        action: 'PAUSE',
        currentTimeSeconds: time,
        isPlaying: false,
      });
    } else if (event.data === YT.PlayerState.ENDED) {
      this.playerEvent.emit({
        action: 'ENDED',
        currentTimeSeconds: time,
        isPlaying: false,
      });
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
