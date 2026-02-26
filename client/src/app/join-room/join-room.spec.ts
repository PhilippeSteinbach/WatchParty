import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { vi } from 'vitest';

import { JoinRoomComponent } from './join-room';
import { WebSocketService } from '../services/websocket.service';
import { RoomService } from '../services/room.service';
import { AuthService } from '../services/auth.service';

describe('JoinRoomComponent', () => {
  let fixture: ComponentFixture<JoinRoomComponent>;
  let component: JoinRoomComponent;

  const connectSpy = vi.fn();
  const disconnectSpy = vi.fn();
  const connectedSignal = signal(false);

  const mockWs = {
    connect: connectSpy,
    disconnect: disconnectSpy,
    connected: connectedSignal.asReadonly(),
  };

  const mockRoomService = {
    getRoom: vi.fn().mockReturnValue(of({})),
  };

  function createComponent(
    roomCode = 'ROOM1234',
    queryNick = '',
    authUser: { displayName: string } | null = null
  ) {
    const currentUserSignal = signal(authUser);
    const mockAuth = { currentUser: currentUserSignal.asReadonly() };

    TestBed.configureTestingModule({
      imports: [JoinRoomComponent],
      providers: [
        { provide: WebSocketService, useValue: mockWs },
        { provide: RoomService, useValue: mockRoomService },
        { provide: AuthService, useValue: mockAuth },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: { get: () => roomCode },
              queryParamMap: { get: () => queryNick },
            },
          },
        },
      ],
    });

    fixture = TestBed.createComponent(JoinRoomComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    connectSpy.mockReset();
    mockRoomService.getRoom.mockReset();
    mockRoomService.getRoom.mockReturnValue(of({}));
  });

  it('should show join form when not logged in and room exists', () => {
    createComponent('ROOM1234', '', null);
    expect(connectSpy).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('form')).toBeTruthy();
  });

  it('should auto-join with displayName when user is logged in', () => {
    createComponent('ROOM1234', '', { displayName: 'Alice' });
    expect(connectSpy).toHaveBeenCalledWith('ROOM1234', 'Alice');
  });

  it('should auto-join with query nickname when not logged in', () => {
    createComponent('ROOM1234', 'GuestBob', null);
    expect(connectSpy).toHaveBeenCalledWith('ROOM1234', 'GuestBob');
  });

  it('should prefer displayName over query nickname when logged in', () => {
    createComponent('ROOM1234', 'GuestBob', { displayName: 'Alice' });
    expect(connectSpy).toHaveBeenCalledWith('ROOM1234', 'Alice');
  });

  it('should show room not found on error', () => {
    mockRoomService.getRoom.mockReturnValue(throwError(() => new Error('404')));
    createComponent('BADCODE', '', null);
    expect(component.roomNotFound()).toBe(true);
  });
});
