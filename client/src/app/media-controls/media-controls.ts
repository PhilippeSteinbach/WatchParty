import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';

@Component({
  selector: 'app-media-controls',
  standalone: true,
  templateUrl: './media-controls.html',
  styleUrl: './media-controls.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MediaControlsComponent {
  readonly isCameraOn = input(false);
  readonly isMicOn = input(false);
  readonly isActive = input(false);

  readonly toggleCamera = output<void>();
  readonly toggleMic = output<void>();
  readonly startMedia = output<void>();
  readonly stopMedia = output<void>();
}
