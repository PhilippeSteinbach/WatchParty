# WatchParty – Implementation Plan

Phasenweise Umsetzung: MVP zuerst lauffähig, dann iterativ erweitern.

---

## Phase 0: Project Scaffolding & Infrastructure

**Goal:** Runnable project structure with build, test, and Docker.

### Server
- [ ] Create .NET 10 Web API project `WatchParty.Api` with solution file `WatchParty.sln` in `server/`
- [ ] Create test project `WatchParty.Api.Tests` (xUnit)
- [ ] Install NuGet packages:
  - `Npgsql.EntityFrameworkCore.PostgreSQL`
  - `FluentValidation.AspNetCore`
  - `Serilog.AspNetCore`
  - `Swashbuckle.AspNetCore`
  - `Microsoft.AspNetCore.Authentication.JwtBearer`
- [ ] Configure `Program.cs`: Swagger, CORS, SignalR, Serilog, Problem Details (RFC 9457), Health Checks
- [ ] Create `appsettings.json` + `appsettings.Development.json` (PostgreSQL connection string, JWT settings, YouTube API key placeholder via User Secrets)
- [ ] Add `.editorconfig` per C# conventions
- [ ] Add `Dockerfile` + `.dockerignore`

### Client
- [ ] Scaffold Angular project in `client/`: `ng new watch-party --standalone --style=scss --routing`
- [ ] Enable strict mode in `tsconfig.json`
- [ ] Install dependencies: `@microsoft/signalr`, `@types/youtube`
- [ ] Create `proxy.conf.json` for API calls in development
- [ ] Add `Dockerfile` (multi-stage: Node build → Nginx)

### Infrastructure
- [ ] Create `docker-compose.yml` in repo root with services: `db` (PostgreSQL 16), `api` (ASP.NET Core), `client` (Nginx)
- [ ] Configure volumes for DB persistence, ports: 4200, 5000, 5432
- [ ] Add CI workflow in `.github/workflows/`: build + test for both projects

### Verification
- `docker compose up` starts all 3 services
- Swagger UI at `localhost:5000/swagger`
- Angular app at `localhost:4200`

---

## Phase 1: MVP – Room System & Synchronized YouTube Player

**Goal:** Create rooms, join via link, watch YouTube videos synchronously. Anonymous participation with nickname.

### Server – Domain & Data
- [ ] Create `Room` entity: `Id` (Guid), `Code` (string, 6-8 chars, unique), `Name`, `ControlMode` (enum: Collaborative | HostOnly), `HostConnectionId`, `CurrentVideoUrl`, `CurrentTime` (TimeSpan), `IsPlaying`, `CreatedAt`, `ExpiresAt`
- [ ] Create `Participant` entity: `Id`, `RoomId`, `Nickname`, `ConnectionId`, `IsHost`, `JoinedAt`
- [ ] Create `AppDbContext` with `DbSet<Room>`, `DbSet<Participant>`, PostgreSQL config
- [ ] Run initial EF Core migration

### Server – Room API (Minimal APIs)
- [ ] `POST /api/rooms` → Create room (Name, ControlMode), return room code
- [ ] `GET /api/rooms/{code}` → Get room details
- [ ] `DELETE /api/rooms/{code}` → Close room (host only)
- [ ] FluentValidation: room name required, max 100 chars
- [ ] Problem Details responses for errors

### Server – SignalR Hub (`WatchPartyHub`)
- [ ] `JoinRoomAsync(roomCode, nickname)` → Add participant to SignalR group, validate nickname, return current player state
- [ ] `LeaveRoomAsync()` → Remove participant, on host leave: assign new host or close room
- [ ] `PlayAsync()` / `PauseAsync()` / `SeekAsync(timeSeconds)` → Check permissions (ControlMode), broadcast to group
- [ ] `ChangeVideoAsync(videoUrl)` → Change video, broadcast
- [ ] `SyncStateAsync()` → Periodic state sync (heartbeat every 5s)
- [ ] Override `OnDisconnectedAsync` for cleanup

### Client – Room Creation & Joining
- [ ] `RoomService` – HTTP calls to Room API, room state as signals
- [ ] `SignalRService` – Manage hub connection, expose events as signals
- [ ] `HomeComponent` – Create room (name, control mode), result: shareable link
- [ ] `JoinRoomComponent` – Enter nickname, join room (route: `/room/:code`)
- [ ] Routing: `/` → Home, `/room/:code` → WatchRoom
- [ ] Model interfaces: `Room`, `Participant`, `PlayerState`, `RoomSettings`

### Client – YouTube Player & Sync
- [ ] `WatchRoomComponent` – Main container with player, participant list
- [ ] `YoutubePlayerComponent` – YouTube IFrame API integration
- [ ] Forward player events (`onStateChange`) to SignalR
- [ ] Apply incoming SignalR events to player (play/pause/seek)
- [ ] Respect control mode: disable controls for non-hosts in Host-Mode
- [ ] `ParticipantListComponent` – Show participants, mark host

### Latency Compensation (Basic)
- [ ] Client sends current playback position to hub periodically
- [ ] Hub compares positions; drift > 2s → `SeekAsync` to drifting client
- [ ] Gradual catchup: playback rate 1.05x for small drift (< 2s)
- [ ] Hard seek: drift > 5s → jump to correct position immediately

### Verification
- Two browser tabs can join a room
- Play/pause/seek from one user is reflected live in the other
- Drift correction works measurably

---

## Phase 2: Live Chat & Playlist

**Goal:** Text chat with emoji reactions and video playlist.

### Server – Chat
- [ ] `ChatMessage` entity: `Id`, `RoomId`, `Nickname`, `Content`, `Reactions` (JSON column), `SentAt`
- [ ] Hub methods: `SendMessageAsync(content)`, `AddReactionAsync(messageId, emoji)`
- [ ] Persist messages in DB (last 200 per room), load history on join
- [ ] Validation: max 500 chars, rate limiting (max 5 messages/10s per user)

### Client – Chat UI
- [ ] `ChatPanelComponent` – Message list with auto-scroll, input field
- [ ] `ChatMessageComponent` – Single message with nickname, timestamp, reaction buttons
- [ ] `EmojiPickerComponent` – Quick reactions (6-8 standard emojis: 👍❤️😂😮😢🔥)
- [ ] Chat state in signals: `messages`, `isLoading`

### Server – Playlist
- [ ] `PlaylistItem` entity: `Id`, `RoomId`, `VideoUrl`, `Title`, `ThumbnailUrl`, `Duration`, `AddedBy`, `Position`, `AddedAt`
- [ ] Hub methods: `AddToPlaylistAsync(videoUrl)`, `PlayNowAsync(videoUrl)`, `RemoveFromPlaylistAsync(itemId)`, `ReorderPlaylistAsync(itemId, newPosition)`, `SkipToNextAsync()`
- [ ] `YouTubeService` – Fetch video metadata (title, thumbnail, duration) from YouTube Data API v3
- [ ] Auto-play next video when current one ends

### Client – Playlist UI
- [ ] `PlaylistPanelComponent` – Video list with drag & drop reorder
- [ ] `AddVideoComponent` – URL input with "Play Now" / "Add to Queue" buttons, video metadata preview
- [ ] `PlaylistService` – Signal-based playlist state
- [ ] Angular CDK `DragDropModule` for reorder

### Verification
- Chat messages appear live for all participants
- Emoji reactions are synchronized
- Videos can be added to playlist and reordered
- "Play Now" replaces the current video for all

---

## Phase 3: Authentication & Persistence

**Goal:** Optional registration, permanent rooms, JWT auth.

### Server – Auth
- [ ] `User` entity: `Id`, `Email`, `DisplayName`, `PasswordHash`, `CreatedAt`
- [ ] Auth endpoints: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`
- [ ] JWT token generation with refresh token rotation
- [ ] Password hashing with BCrypt
- [ ] SignalR Hub supports both anonymous and authenticated users

### Server – Permanent Rooms
- [ ] Extend Room entity: `OwnerId` (FK → User, nullable), `IsPermanent` (bool)
- [ ] Anonymous rooms: `ExpiresAt` = CreatedAt + 24h
- [ ] Cleanup job (`IHostedService`): delete expired rooms
- [ ] Registered users: can create permanent rooms (no ExpiresAt)
- [ ] `GET /api/users/me/rooms` → List own rooms

### Client – Auth UI
- [ ] `LoginComponent`, `RegisterComponent` – Reactive forms with validation
- [ ] `AuthService` – JWT in localStorage, auth state as signal (`currentUser`)
- [ ] `AuthInterceptor` – Attach Bearer token to API requests
- [ ] `AuthGuard` – Protected routes (e.g., "My Rooms")
- [ ] `UserMenuComponent` – Login/logout, nickname display
- [ ] Route: `/my-rooms` → List of own permanent rooms

### Verification
- Registration + login works, JWT is correctly sent
- Permanent rooms survive server restarts
- Anonymous rooms are cleaned up after 24h
- Non-logged-in users can still create and join rooms

---

## Phase 4: WebRTC Audio/Video & Polish

**Goal:** Webcam/microphone sharing, UI polish, production readiness.

### Server – WebRTC Signaling
- [ ] Hub methods: `SendOfferAsync(targetConnectionId, sdp)`, `SendAnswerAsync(targetConnectionId, sdp)`, `SendIceCandidateAsync(targetConnectionId, candidate)`
- [ ] Use public STUN servers initially (Google)
- [ ] Connection limit: max 6 participants per room for WebRTC (mesh topology)

### Client – WebRTC
- [ ] `WebRtcService` – `RTCPeerConnection` management, media stream handling
- [ ] `getUserMedia()` for camera/microphone access
- [ ] Mesh network: each peer connects to every other peer
- [ ] Signaling via SignalR Hub (offer/answer/ICE)
- [ ] `VideoGridComponent` – Webcam feeds in responsive grid layout (max 6)
- [ ] `MediaControlsComponent` – Camera on/off, microphone mute/unmute
- [ ] Media state as signals: `localStream`, `remoteStreams`, `isCameraOn`, `isMicOn`

### UI/UX Polish
- [ ] Responsive layout: sidebar (chat/playlist) + main area (player + video grid)
- [ ] Dark theme as default (SCSS variables)
- [ ] Participant notifications: join/leave toasts
- [ ] Room link copy button (Clipboard API)
- [ ] Keyboard shortcuts: Space (play/pause), M (mute), F (fullscreen)
- [ ] Loading states and error handling for all async operations
- [ ] Accessibility: keyboard navigation, ARIA labels, focus management

### Production Readiness
- [ ] Rate-limiting middleware (ASP.NET Core `RateLimiter`)
- [ ] CORS policy for production origin
- [ ] Health checks: DB, SignalR
- [ ] Serilog: structured logging to stdout (Docker-friendly)
- [ ] Docker Compose production override: Nginx with SSL termination, environment variables
- [ ] EF Core migration on startup (`Database.MigrateAsync()` in `Program.cs`)

### Verification
- Webcam/mic sharing works between 2+ participants
- Video grid adapts to number of streams
- Mute/camera toggle works
- Full app works end-to-end via `docker compose up`

---

## Overall Verification Matrix

| Check | Command |
|-------|---------|
| Server Build | `dotnet build` (no warnings) |
| Server Tests | `dotnet test` (all green) |
| Client Build | `ng build` (no errors) |
| Client Lint | `ng lint` (clean) |
| Client Tests | `ng test --watch=false` (all green) |
| Full Stack | `docker compose up` → app at `localhost:4200` |
| E2E Smoke | Create room → join → play video → send chat → verify sync in 2nd tab |