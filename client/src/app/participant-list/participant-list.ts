import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { LucideAngularModule, Crown, Maximize2 } from 'lucide-angular';
import { Participant } from '../models/room.model';

@Component({
  selector: 'app-participant-list',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './participant-list.html',
  styleUrl: './participant-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParticipantListComponent {
  protected readonly CrownIcon = Crown;
  protected readonly Maximize2Icon = Maximize2;

  readonly participants = input<Participant[]>([]);
  /** Connection IDs of participants who have an active webcam stream */
  readonly activeWebcamIds = input<Set<string>>(new Set());
  /** Which webcam is currently focused (null = none) */
  readonly focusedWebcamId = input<string | null>(null);
  /** Emits the connectionId of a participant whose webcam should be focused */
  readonly focusWebcam = output<string>();

  hasActiveWebcam(connectionId: string): boolean {
    return this.activeWebcamIds().has(connectionId);
  }

  isFocused(connectionId: string): boolean {
    return this.focusedWebcamId() === connectionId;
  }
}
