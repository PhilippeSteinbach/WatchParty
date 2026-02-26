import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';

@Component({
  selector: 'app-video-controls',
  standalone: true,
  templateUrl: './video-controls.html',
  styleUrl: './video-controls.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VideoControlsComponent {
  readonly isPlaying = input(false);
  readonly currentTime = input(0);
  readonly duration = input(0);
  readonly canControl = input(false);
  readonly hasVideo = input(false);

  readonly playPause = output<void>();
  readonly seek = output<number>();
  readonly volumeChange = output<number>();

  protected readonly isSeeking = signal(false);
  protected readonly seekTime = signal(0);
  protected readonly volume = signal(100);
  protected readonly isMuted = signal(false);
  private savedVolume = 100;

  protected readonly displayTime = computed(() =>
    this.isSeeking() ? this.seekTime() : this.currentTime()
  );

  protected readonly progressPercent = computed(() => {
    const dur = this.duration();
    if (dur <= 0) return 0;
    return (this.displayTime() / dur) * 100;
  });

  togglePlayPause(): void {
    this.playPause.emit();
  }

  onSeekStart(): void {
    this.isSeeking.set(true);
    this.seekTime.set(this.currentTime());
  }

  onSeeking(event: Event): void {
    this.seekTime.set(+(event.target as HTMLInputElement).value);
  }

  onSeekEnd(event: Event): void {
    const time = +(event.target as HTMLInputElement).value;
    this.isSeeking.set(false);
    this.seek.emit(time);
  }

  onVolumeChange(event: Event): void {
    const vol = +(event.target as HTMLInputElement).value;
    this.volume.set(vol);
    this.isMuted.set(vol === 0);
    this.volumeChange.emit(vol);
  }

  toggleMute(): void {
    if (this.isMuted()) {
      this.volume.set(this.savedVolume);
      this.isMuted.set(false);
      this.volumeChange.emit(this.savedVolume);
    } else {
      this.savedVolume = this.volume();
      this.volume.set(0);
      this.isMuted.set(true);
      this.volumeChange.emit(0);
    }
  }

  formatTime(seconds: number): string {
    if (!seconds || seconds < 0) return '0:00';
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);
    if (hrs > 0) {
      return `${hrs}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  }
}
