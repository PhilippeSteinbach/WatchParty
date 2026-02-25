import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { RoomService } from '../services/room.service';
import { ControlMode } from '../models/room.model';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './home.html',
  styleUrl: './home.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly roomService = inject(RoomService);
  private readonly router = inject(Router);

  readonly roomName = signal('');
  readonly controlMode = signal<ControlMode>('COLLABORATIVE');
  readonly nickname = signal('');
  readonly error = signal('');
  readonly creating = signal(false);

  createRoom(): void {
    const name = this.roomName().trim();
    const nick = this.nickname().trim();
    if (!name || !nick) {
      this.error.set('Please enter a room name and nickname.');
      return;
    }

    this.creating.set(true);
    this.error.set('');

    this.roomService.createRoom(name, this.controlMode()).subscribe({
      next: (room) => {
        this.router.navigate(['/room', room.code], {
          queryParams: { nickname: nick },
        });
      },
      error: () => {
        this.error.set('Failed to create room. Please try again.');
        this.creating.set(false);
      },
    });
  }
}
