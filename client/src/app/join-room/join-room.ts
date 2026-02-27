import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
  OnInit,
  OnDestroy,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Film, AlertCircle } from 'lucide-angular';
import { WebSocketService } from '../services/websocket.service';
import { RoomService } from '../services/room.service';
import { WatchRoomComponent } from '../watch-room/watch-room';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-join-room',
  standalone: true,
  imports: [FormsModule, WatchRoomComponent, LucideAngularModule],
  templateUrl: './join-room.html',
  styleUrl: './join-room.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JoinRoomComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly ws = inject(WebSocketService);
  private readonly roomService = inject(RoomService);
  private readonly auth = inject(AuthService);

  readonly FilmIcon = Film;
  readonly AlertCircleIcon = AlertCircle;

  readonly nickname = signal('');
  readonly roomCode = signal('');
  readonly error = signal('');
  readonly roomNotFound = signal(false);
  readonly loading = signal(true);
  readonly connected = this.ws.connected;

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('code') ?? '';
    this.roomCode.set(code);

    const queryNick = this.route.snapshot.queryParamMap.get('nickname') ?? '';
    if (queryNick) {
      this.nickname.set(queryNick);
    }

    // Verify room exists, then auto-join if logged in
    this.roomService.getRoom(code).subscribe({
      next: () => {
        const user = this.auth.currentUser();
        if (user) {
          this.nickname.set(user.displayName);
          this.join();
        } else if (queryNick) {
          this.join();
        } else {
          this.loading.set(false);
        }
      },
      error: () => {
        this.roomNotFound.set(true);
        this.error.set('Room not found.');
        this.loading.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    this.ws.disconnect();
  }

  join(): void {
    const nick = this.nickname().trim();
    if (!nick) {
      this.error.set('Please enter a nickname.');
      return;
    }
    this.error.set('');
    this.ws.connect(this.roomCode(), nick);
  }
}
