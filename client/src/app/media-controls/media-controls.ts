import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';
import {
  LucideAngularModule,
  Video,
  VideoOff,
  Mic,
  MicOff,
  X,
  Phone,
} from 'lucide-angular';

@Component({
  selector: 'app-media-controls',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './media-controls.html',
  styleUrl: './media-controls.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MediaControlsComponent {
  protected readonly VideoIcon = Video;
  protected readonly VideoOffIcon = VideoOff;
  protected readonly MicIcon = Mic;
  protected readonly MicOffIcon = MicOff;
  protected readonly XIcon = X;
  protected readonly PhoneIcon = Phone;

  readonly isCameraOn = input(false);
  readonly isMicOn = input(false);
  readonly isActive = input(false);

  readonly toggleCamera = output<void>();
  readonly toggleMic = output<void>();
  readonly startMedia = output<void>();
  readonly stopMedia = output<void>();
}
