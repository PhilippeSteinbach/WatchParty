import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ControlMode, Room } from '../models/room.model';

@Injectable({ providedIn: 'root' })
export class RoomService {
  private readonly http = inject(HttpClient);

  createRoom(name: string, controlMode: ControlMode, isPermanent = false): Observable<Room> {
    return this.http.post<Room>('/api/rooms', { name, controlMode, isPermanent });
  }

  getRoom(code: string): Observable<Room> {
    return this.http.get<Room>(`/api/rooms/${code}`);
  }

  deleteRoom(code: string): Observable<void> {
    return this.http.delete<void>(`/api/rooms/${code}`);
  }

  renameRoom(code: string, name: string): Observable<Room> {
    return this.http.patch<Room>(`/api/rooms/${code}`, { name });
  }

  getMyRooms(): Observable<Room[]> {
    return this.http.get<Room[]>('/api/users/me/rooms');
  }
}
