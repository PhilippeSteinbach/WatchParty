# WatchParty – Architecture

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Angular (latest, standalone components), TypeScript, SCSS |
| Backend | ASP.NET Core (.NET 10+), C# 14 |
| Database | PostgreSQL 16 |
| Real-time | SignalR (watch-party synchronisation, chat, WebRTC signaling) |
| Video | YouTube IFrame API (playback) + YouTube Data API v3 (metadata) |
| Audio/Video | WebRTC Mesh (up to 6 participants) |
| Containerisation | Docker Compose (Nginx, API, PostgreSQL) |
| Testing (FE) | Jasmine, Karma; Cypress / Playwright for E2E |
| Testing (BE) | xUnit; Moq / NSubstitute for mocks |
| API Style | Minimal APIs + SignalR; OpenAPI / Swagger docs |

---

## System Overview

```
┌─────────────────────────────────────────────────────┐
│                    Docker Compose                    │
│                                                     │
│  ┌──────────┐   ┌───────────────┐   ┌───────────┐  │
│  │  Nginx   │   │  ASP.NET Core │   │ PostgreSQL│  │
│  │ (Angular)│──▶│  API + SignalR │──▶│   16      │  │
│  │  :4200   │   │    :5000      │   │  :5432    │  │
│  └──────────┘   └───────┬───────┘   └───────────┘  │
│                         │                           │
│                   YouTube Data                      │
│                    API v3                            │
└─────────────────────────────────────────────────────┘
```

---

## Service Communication

```
Client (Angular)              Server (ASP.NET Core)
├── SignalRService       ◄──► WatchPartyHub
├── WebRtcService        ◄──► (Signaling via Hub)
├── RoomService          ◄──► RoomEndpoints
├── AuthService          ◄──► AuthEndpoints
├── PlaylistService      ◄──► (via Hub)
└── YoutubePlayerComp.        YouTubeService (Data API v3)
```

---

## Server Project Structure

```
server/
├── WatchParty.sln
├── src/WatchParty.Api/
│   ├── Program.cs
│   ├── appsettings.json
│   ├── Data/
│   │   ├── AppDbContext.cs
│   │   └── Migrations/
│   ├── Entities/
│   │   ├── Room.cs
│   │   ├── Participant.cs
│   │   ├── ChatMessage.cs
│   │   ├── PlaylistItem.cs
│   │   └── User.cs
│   ├── Hubs/
│   │   └── WatchPartyHub.cs
│   ├── Services/
│   │   ├── IRoomService.cs / RoomService.cs
│   │   ├── IYouTubeService.cs / YouTubeService.cs
│   │   └── ITokenService.cs / TokenService.cs
│   ├── Endpoints/
│   │   ├── RoomEndpoints.cs
│   │   └── AuthEndpoints.cs
│   ├── Validators/
│   │   └── CreateRoomValidator.cs
│   └── Middleware/
│       └── ExceptionMiddleware.cs
├── tests/WatchParty.Api.Tests/
│   ├── Services/
│   ├── Hubs/
│   └── Endpoints/
└── Dockerfile
```

---

## Client Project Structure

```
client/
├── angular.json
├── src/
│   ├── app/
│   │   ├── app.component.ts
│   │   ├── app.routes.ts
│   │   ├── core/
│   │   │   ├── services/
│   │   │   │   ├── signalr.service.ts
│   │   │   │   ├── auth.service.ts
│   │   │   │   └── webrtc.service.ts
│   │   │   ├── interceptors/
│   │   │   │   └── auth.interceptor.ts
│   │   │   ├── guards/
│   │   │   │   └── auth.guard.ts
│   │   │   └── models/
│   │   │       ├── room.model.ts
│   │   │       ├── participant.model.ts
│   │   │       └── chat-message.model.ts
│   │   ├── features/
│   │   │   ├── home/
│   │   │   │   └── home.component.ts
│   │   │   ├── watch-room/
│   │   │   │   ├── watch-room.component.ts
│   │   │   │   ├── youtube-player.component.ts
│   │   │   │   ├── participant-list.component.ts
│   │   │   │   ├── chat-panel.component.ts
│   │   │   │   ├── playlist-panel.component.ts
│   │   │   │   └── video-grid.component.ts
│   │   │   └── auth/
│   │   │       ├── login.component.ts
│   │   │       └── register.component.ts
│   │   └── shared/
│   │       ├── components/
│   │       └── pipes/
│   ├── styles/
│   │   ├── _variables.scss
│   │   └── styles.scss
│   └── environments/
├── Dockerfile
└── proxy.conf.json
```

---

## Domain Model

```
┌──────────┐       ┌──────────────┐       ┌──────────────┐
│   User   │1─────*│    Room      │1─────*│ Participant  │
│          │       │              │       │              │
│ Id       │       │ Id           │       │ Id           │
│ Email    │       │ Code (unique)│       │ RoomId       │
│ Display  │       │ Name         │       │ Nickname     │
│ PwdHash  │       │ ControlMode  │       │ ConnectionId │
│ CreatedAt│       │ HostConnId   │       │ IsHost       │
└──────────┘       │ CurrentVideo │       │ JoinedAt     │
                   │ CurrentTime  │       └──────────────┘
                   │ IsPlaying    │
                   │ OwnerId?     │       ┌──────────────┐
                   │ IsPermanent  │1─────*│ PlaylistItem │
                   │ CreatedAt    │       │              │
                   │ ExpiresAt?   │       │ Id           │
                   └──────┬───────┘       │ RoomId       │
                          │               │ VideoUrl     │
                          │1              │ Title        │
                          │               │ ThumbnailUrl │
                          *               │ Duration     │
                   ┌──────────────┐       │ AddedBy      │
                   │ ChatMessage  │       │ Position     │
                   │              │       │ AddedAt      │
                   │ Id           │       └──────────────┘
                   │ RoomId       │
                   │ Nickname     │
                   │ Content      │
                   │ Reactions    │  ← JSON column
                   │ SentAt       │
                   └──────────────┘
```

---

## SignalR Hub Methods (`WatchPartyHub`)

### Room Management
| Method | Direction | Description |
|--------|-----------|-------------|
| `JoinRoomAsync(code, nickname)` | Client → Server | Join room, receive current state |
| `LeaveRoomAsync()` | Client → Server | Leave room, cleanup |
| `OnDisconnectedAsync()` | Server | Auto-cleanup on disconnect |

### Player Sync
| Method | Direction | Description |
|--------|-----------|-------------|
| `PlayAsync()` | Client → Server → All | Broadcast play |
| `PauseAsync()` | Client → Server → All | Broadcast pause |
| `SeekAsync(seconds)` | Client → Server → All | Broadcast seek |
| `ChangeVideoAsync(url)` | Client → Server → All | Switch video |
| `SyncStateAsync()` | Bidirectional | Periodic heartbeat (5s) |

### Chat
| Method | Direction | Description |
|--------|-----------|-------------|
| `SendMessageAsync(content)` | Client → Server → All | Broadcast chat message |
| `AddReactionAsync(msgId, emoji)` | Client → Server → All | Add emoji reaction |

### Playlist
| Method | Direction | Description |
|--------|-----------|-------------|
| `AddToPlaylistAsync(url)` | Client → Server → All | Add video to queue |
| `PlayNowAsync(url)` | Client → Server → All | Immediately play video |
| `RemoveFromPlaylistAsync(id)` | Client → Server → All | Remove from queue |
| `ReorderPlaylistAsync(id, pos)` | Client → Server → All | Change order |
| `SkipToNextAsync()` | Client → Server → All | Play next in queue |

### WebRTC Signaling
| Method | Direction | Description |
|--------|-----------|-------------|
| `SendOfferAsync(target, sdp)` | Client → Server → Target | WebRTC offer |
| `SendAnswerAsync(target, sdp)` | Client → Server → Target | WebRTC answer |
| `SendIceCandidateAsync(target, candidate)` | Client → Server → Target | ICE candidate |

---

## REST Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/rooms` | Optional | Create room |
| `GET` | `/api/rooms/{code}` | No | Get room details |
| `DELETE` | `/api/rooms/{code}` | Yes (Host) | Close room |
| `POST` | `/api/auth/register` | No | Register user |
| `POST` | `/api/auth/login` | No | Login, get JWT |
| `POST` | `/api/auth/refresh` | No | Refresh token |
| `GET` | `/api/users/me/rooms` | Yes | List own rooms |

---

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Database | PostgreSQL | Production-ready, Docker-friendly, JSON column support for reactions |
| API Style | Minimal APIs | Lightweight, fits feature-endpoint approach |
| WebRTC Topology | Mesh (max 6) | No SFU/media server needed, signaling via existing SignalR hub |
| UI Framework | Custom SCSS, no Angular Material | Lighter bundle, dark theme, full control |
| YouTube Integration | IFrame API + Data API v3 | IFrame for playback, Data API for metadata |
| State Management | Angular Signals | Built-in, fine-grained reactivity, no external deps |
| Auth | Optional JWT | Anonymous users supported, registered users get permanent rooms |
| Deployment | Docker Compose | Full stack locally and in production |

---

## Latency Compensation Strategy

| Drift | Action |
|-------|--------|
| < 2s | Gradual catchup: playback rate 1.05x until synced |
| 2–5s | Seek to correct position |
| > 5s | Hard seek + pause/resume |

Clients report playback position to hub every 5 seconds. Hub compares and issues corrections to drifting clients.