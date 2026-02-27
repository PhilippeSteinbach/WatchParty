import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Plus, Trash2, AlertTriangle, Pencil, Check, X } from 'lucide-angular';
import { Room } from '../../models/room.model';
import { RoomService } from '../../services/room.service';

@Component({
  selector: 'app-room-settings',
  standalone: true,
  imports: [RouterLink, FormsModule, LucideAngularModule],
  templateUrl: './room-settings.html',
})
export class RoomSettingsComponent implements OnInit {
  private readonly roomService = inject(RoomService);

  readonly PlusIcon = Plus;
  readonly Trash2Icon = Trash2;
  readonly AlertTriangleIcon = AlertTriangle;
  readonly PencilIcon = Pencil;
  readonly CheckIcon = Check;
  readonly XIcon = X;

  readonly rooms = signal<Room[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly confirmDeleteCode = signal<string | null>(null);
  readonly editingCode = signal<string | null>(null);
  readonly editingName = signal('');

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

  startEditing(room: Room): void {
    this.editingCode.set(room.code);
    this.editingName.set(room.name);
  }

  cancelEditing(): void {
    this.editingCode.set(null);
    this.editingName.set('');
  }

  saveRename(): void {
    const code = this.editingCode();
    const name = this.editingName().trim();
    if (!code || !name) return;
    this.roomService.renameRoom(code, name).subscribe({
      next: (updated) => {
        this.rooms.update(rooms => rooms.map(r => r.code === code ? { ...r, name: updated.name } : r));
        this.editingCode.set(null);
      },
      error: () => this.error.set('Failed to rename room.'),
    });
  }
}
