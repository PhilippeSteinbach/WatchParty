import { ChangeDetectionStrategy, Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { RoomService } from '../services/room.service';
import { AuthService } from '../services/auth.service';
import { ControlMode } from '../models/room.model';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent implements OnInit {
  private readonly roomService = inject(RoomService);
  private readonly route = inject(ActivatedRoute);
  readonly router = inject(Router);
  readonly auth = inject(AuthService);

  readonly view = signal<'landing' | 'create' | 'join'>('landing');
  readonly roomName = signal('');
  readonly controlMode = signal<ControlMode>('COLLABORATIVE');
  readonly nickname = signal('');
  readonly joinCode = signal('');
  readonly error = signal('');
  readonly creating = signal(false);

  ngOnInit(): void {
    const v = this.route.snapshot.queryParamMap.get('view');
    if (v === 'create' || v === 'join') {
      this.view.set(v);
    }
  }

  createRoom(): void {
    const name = this.roomName().trim();
    const nick = this.auth.isLoggedIn()
      ? (this.auth.currentUser()?.displayName ?? '')
      : this.nickname().trim();

    if (!name || !nick) {
      this.error.set('Please fill in all fields.');
      return;
    }

    this.creating.set(true);
    this.error.set('');

    const isPermanent = this.auth.isLoggedIn();

    this.roomService.createRoom(name, this.controlMode(), isPermanent).subscribe({
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

  joinRoom(): void {
    const code = this.joinCode().trim().toUpperCase();
    if (!code) {
      this.error.set('Please enter a room code.');
      return;
    }
    this.router.navigate(['/room', code]);
  }

  showView(v: 'create' | 'join'): void {
    this.error.set('');
    this.view.set(v);
  }
}
