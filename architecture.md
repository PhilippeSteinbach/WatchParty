# WatchParty – Architecture

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Angular (latest, standalone components), TypeScript, SCSS |
| Backend | Java 21+, Spring Boot 3 |
| Database | PostgreSQL 16 |
| Real-time | Spring WebSocket (STOMP over SockJS) |
| Video | YouTube IFrame API (playback) + YouTube Data API v3 (metadata) |
| Audio/Video | WebRTC Mesh (up to 6 participants) |
| Containerisation | Docker Compose (Nginx, API, PostgreSQL) |
| Testing (FE) | Jasmine, Karma; Cypress / Playwright for E2E |
| Testing (BE) | JUnit 5; Mockito for mocks |
| API Style | Spring REST Controllers + WebSocket; SpringDoc OpenAPI / Swagger docs |

---

## System Overview

```
┌─────────────────────────────────────────────────────────┐
│                      Docker Compose                      │
│                                                         │
│  ┌──────────┐   ┌────────────────────┐   ┌───────────┐  │
│  │  Nginx   │   │  Spring Boot       │   │ PostgreSQL│  │
│  │ (Angular)│──▶│  API + WebSocket   │──▶│   16      │  │
│  │  :4200   │   │    :8080           │   │  :5432    │  │
│  └──────────┘   └────────┬───────────┘   └───────────┘  │
│                          │                               │
│                    YouTube Data                          │
│                     API v3                               │
└─────────────────────────────────────────────────────────┘
```

---

## Service Communication

```
Client (Angular)              Server (Spring Boot)
├── WebSocketService    ◄──► WatchPartyWebSocketHandler
├── WebRtcService       ◄──► (Signaling via WebSocket)
├── RoomService         ◄──► RoomController
├── AuthService         ◄──► AuthController
├── PlaylistService     ◄──► (via WebSocket)
└── YoutubePlayerComp.        YouTubeService (Data API v3)
```

---

## Server Project Structure

```
server/
├── pom.xml
├── src/main/java/com/watchparty/
│   ├── WatchPartyApplication.java
│   ├── config/
│   │   ├── WebSocketConfig.java
│   │   ├── SecurityConfig.java
│   │   └── CorsConfig.java
│   ├── entity/
│   │   ├── Room.java
│   │   ├── Participant.java
│   │   ├── ChatMessage.java
│   │   ├── PlaylistItem.java
│   │   └── User.java
│   ├── repository/
│   │   ├── RoomRepository.java
│   │   ├── ParticipantRepository.java
│   │   ├── ChatMessageRepository.java
│   │   ├── PlaylistItemRepository.java
│   │   └── UserRepository.java
│   ├── service/
│   │   ├── RoomService.java
│   │   ├── YouTubeService.java
│   │   └── TokenService.java
│   ├── controller/
│   │   ├── RoomController.java
│   │   └── AuthController.java
│   ├── websocket/
│   │   └── WatchPartyWebSocketHandler.java
│   ├── dto/
│   │   ├── CreateRoomRequest.java
│   │   └── RoomResponse.java
│   └── exception/
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── db/migration/        (Flyway)
├── src/test/java/com/watchparty/
│   ├── service/
│   ├── controller/
│   └── websocket/
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
│   │   │   │   ├── websocket.service.ts
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

## WebSocket Message Handlers (`WatchPartyWebSocketHandler`)

### Room Management
| Method | Direction | Description |
|--------|-----------|-------------|
| `joinRoom(code, nickname)` | Client → Server | Join room, receive current state |
| `leaveRoom()` | Client → Server | Leave room, cleanup |
| `onDisconnect()` | Server | Auto-cleanup on disconnect |

### Player Sync
| Method | Direction | Description |
|--------|-----------|-------------|
| `play()` | Client → Server → All | Broadcast play |
| `pause()` | Client → Server → All | Broadcast pause |
| `seek(seconds)` | Client → Server → All | Broadcast seek |
| `changeVideo(url)` | Client → Server → All | Switch video |
| `syncState()` | Bidirectional | Periodic heartbeat (5s) |

### Chat
| Method | Direction | Description |
|--------|-----------|-------------|
| `sendMessage(content)` | Client → Server → All | Broadcast chat message |
| `addReaction(msgId, emoji)` | Client → Server → All | Add emoji reaction |

### Playlist
| Method | Direction | Description |
|--------|-----------|-------------|
| `addToPlaylist(url)` | Client → Server → All | Add video to queue |
| `playNow(url)` | Client → Server → All | Immediately play video |
| `removeFromPlaylist(id)` | Client → Server → All | Remove from queue |
| `reorderPlaylist(id, pos)` | Client → Server → All | Change order |
| `skipToNext()` | Client → Server → All | Play next in queue |

### WebRTC Signaling
| Method | Direction | Description |
|--------|-----------|-------------|
| `sendOffer(target, sdp)` | Client → Server → Target | WebRTC offer |
| `sendAnswer(target, sdp)` | Client → Server → Target | WebRTC answer |
| `sendIceCandidate(target, candidate)` | Client → Server → Target | ICE candidate |

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
| API Style | Spring REST Controllers | Standard, well-supported, fits feature-based approach |
| WebRTC Topology | Mesh (max 6) | No SFU/media server needed, signaling via existing WebSocket |
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

Clients report playback position to server every 5 seconds. Server compares and issues corrections to drifting clients.