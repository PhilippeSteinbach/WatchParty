import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Participant } from '../models/room.model';

@Component({
  selector: 'app-participant-list',
  standalone: true,
  templateUrl: './participant-list.html',
  styleUrl: './participant-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParticipantListComponent {
  readonly participants = input<Participant[]>([]);
}
