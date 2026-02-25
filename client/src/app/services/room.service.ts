import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ControlMode, Room } from '../models/room.model';

@Injectable({ providedIn: 'root' })
export class RoomService {
  private readonly http = inject(HttpClient);

  createRoom(name: string, controlMode: ControlMode): Observable<Room> {
    return this.http.post<Room>('/api/rooms', { name, controlMode });
  }

  getRoom(code: string): Observable<Room> {
    return this.http.get<Room>(`/api/rooms/${code}`);
  }

  deleteRoom(code: string): Observable<void> {
    return this.http.delete<void>(`/api/rooms/${code}`);
  }
}
