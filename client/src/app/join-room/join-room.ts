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
import { WebSocketService } from '../services/websocket.service';
import { RoomService } from '../services/room.service';
import { WatchRoomComponent } from '../watch-room/watch-room';

@Component({
  selector: 'app-join-room',
  standalone: true,
  imports: [FormsModule, WatchRoomComponent],
  templateUrl: './join-room.html',
  styleUrl: './join-room.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JoinRoomComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly ws = inject(WebSocketService);
  private readonly roomService = inject(RoomService);

  readonly nickname = signal('');
  readonly roomCode = signal('');
  readonly error = signal('');
  readonly roomNotFound = signal(false);
  readonly connected = this.ws.connected;

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('code') ?? '';
    this.roomCode.set(code);

    const queryNick = this.route.snapshot.queryParamMap.get('nickname') ?? '';
    if (queryNick) {
      this.nickname.set(queryNick);
      this.join();
    }

    // Verify room exists
    this.roomService.getRoom(code).subscribe({
      error: () => {
        this.roomNotFound.set(true);
        this.error.set('Room not found.');
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
