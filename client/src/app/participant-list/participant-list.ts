import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { LucideAngularModule, Crown } from 'lucide-angular';
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

  readonly participants = input<Participant[]>([]);
}
