import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Room } from '../models/room.model';
import { RoomService } from '../services/room.service';

@Component({
  selector: 'app-my-rooms',
  imports: [RouterLink],
  templateUrl: './my-rooms.html',
  styleUrl: './my-rooms.scss'
})
export class MyRoomsComponent implements OnInit {
  private readonly roomService = inject(RoomService);

  readonly rooms = signal<Room[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly confirmDeleteCode = signal<string | null>(null);

  ngOnInit(): void {
    this.roomService.getMyRooms().subscribe({
      next: (rooms) => { this.rooms.set(rooms); this.loading.set(false); },
      error: () => { this.error.set('Failed to load rooms.'); this.loading.set(false); }
    });
  }

  confirmDelete(code: string): void {
    this.confirmDeleteCode.set(code);
  }

  cancelDelete(): void {
    this.confirmDeleteCode.set(null);
  }

  deleteRoom(): void {
    const code = this.confirmDeleteCode();
    if (!code) return;
    this.confirmDeleteCode.set(null);
    this.roomService.deleteRoom(code).subscribe({
      next: () => this.rooms.update(rooms => rooms.filter(r => r.code !== code)),
      error: () => this.error.set('Failed to delete room.'),
    });
  }
}
