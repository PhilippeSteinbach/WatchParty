# WatchParty – Implementation Plan

Phasenweise Umsetzung: MVP zuerst lauffähig, dann iterativ erweitern.

---

## Phase 0: Project Scaffolding & Infrastructure

**Goal:** Runnable project structure with build, test, and Docker.

### Server
- [ ] Create Spring Boot 3 project with Maven in `server/` (Java 21+)
- [ ] Add dependencies in `pom.xml`:
  - `spring-boot-starter-web`
  - `spring-boot-starter-data-jpa`
  - `spring-boot-starter-websocket`
  - `spring-boot-starter-validation`
  - `spring-boot-starter-security`
  - `springdoc-openapi-starter-webmvc-ui`
  - `postgresql` driver
  - `flyway-core`
  - `jjwt` (io.jsonwebtoken)
- [ ] Configure `WatchPartyApplication.java` main class
- [ ] Create `application.yml` + `application-dev.yml` (PostgreSQL connection, JWT settings, YouTube API key placeholder via environment variables)
- [ ] Add `Dockerfile` + `.dockerignore`

### Client
- [ ] Scaffold Angular project in `client/`: `ng new watch-party --standalone --style=scss --routing`
- [ ] Enable strict mode in `tsconfig.json`
- [ ] Install dependencies: `@stomp/stompjs`, `sockjs-client`, `@types/youtube`
- [ ] Create `proxy.conf.json` for API calls in development
- [ ] Add `Dockerfile` (multi-stage: Node build → Nginx)

### Infrastructure
- [ ] Create `docker-compose.yml` in repo root with services: `db` (PostgreSQL 16), `api` (Spring Boot), `client` (Nginx)
- [ ] Configure volumes for DB persistence, ports: 4200, 8080, 5432
- [ ] Add CI workflow in `.github/workflows/`: build + test for both projects

### Verification
- `docker compose up` starts all 3 services
- Swagger UI at `localhost:8080/swagger-ui.html`
- Angular app at `localhost:4200`

---

## Phase 1: MVP – Room System & Synchronized YouTube Player

**Goal:** Create rooms, join via link, watch YouTube videos synchronously. Anonymous participation with nickname.

### Server – Domain & Data
- [ ] Create `Room` entity (JPA `@Entity`): `id` (UUID), `code` (String, 6-8 chars, unique), `name`, `controlMode` (enum: COLLABORATIVE | HOST_ONLY), `hostConnectionId`, `currentVideoUrl`, `currentTime` (Duration), `isPlaying`, `createdAt`, `expiresAt`
- [ ] Create `Participant` entity: `id`, `roomId`, `nickname`, `connectionId`, `isHost`, `joinedAt`
- [ ] Create `RoomRepository` and `ParticipantRepository` (Spring Data JPA)
- [ ] Create initial Flyway migration

### Server – Room API (REST Controllers)
- [ ] `POST /api/rooms` → Create room (Name, ControlMode), return room code
- [ ] `GET /api/rooms/{code}` → Get room details
- [ ] `DELETE /api/rooms/{code}` → Close room (host only)
- [ ] Jakarta Bean Validation: room name required, max 100 chars
- [ ] Problem Details responses for errors (RFC 9457)

### Server – WebSocket Handler (`WatchPartyWebSocketHandler`)
- [ ] `joinRoom(roomCode, nickname)` → Add participant to session group, validate nickname, return current player state
- [ ] `leaveRoom()` → Remove participant, on host leave: assign new host or close room
- [ ] `play()` / `pause()` / `seek(timeSeconds)` → Check permissions (ControlMode), broadcast to group
- [ ] `changeVideo(videoUrl)` → Change video, broadcast
- [ ] `syncState()` → Periodic state sync (heartbeat every 5s)
- [ ] Handle session disconnect for cleanup

### Client – Room Creation & Joining
- [ ] `RoomService` – HTTP calls to Room API, room state as signals
- [ ] `WebSocketService` – Manage STOMP/SockJS connection, expose events as signals
- [ ] `HomeComponent` – Create room (name, control mode), result: shareable link
- [ ] `JoinRoomComponent` – Enter nickname, join room (route: `/room/:code`)
- [ ] Routing: `/` → Home, `/room/:code` → WatchRoom
- [ ] Model interfaces: `Room`, `Participant`, `PlayerState`, `RoomSettings`

### Client – YouTube Player & Sync
- [ ] `WatchRoomComponent` – Main container with player, participant list
- [ ] `YoutubePlayerComponent` – YouTube IFrame API integration
- [ ] Forward player events (`onStateChange`) to WebSocket
- [ ] Apply incoming WebSocket events to player (play/pause/seek)
- [ ] Respect control mode: disable controls for non-hosts in Host-Mode
- [ ] `ParticipantListComponent` – Show participants, mark host

### Latency Compensation (Basic)
- [ ] Client sends current playback position to server periodically
- [ ] Server compares positions; drift > 2s → `seek` to drifting client
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
- [ ] `ChatMessage` entity (JPA `@Entity`): `id`, `roomId`, `nickname`, `content`, `reactions` (JSON column via `@JdbcTypeCode`), `sentAt`
- [ ] WebSocket message handlers: `sendMessage(content)`, `addReaction(messageId, emoji)`
- [ ] Persist messages in DB (last 200 per room), load history on join
- [ ] Validation: max 500 chars, rate limiting (max 5 messages/10s per user)

### Client – Chat UI
- [ ] `ChatPanelComponent` – Message list with auto-scroll, input field
- [ ] `ChatMessageComponent` – Single message with nickname, timestamp, reaction buttons
- [ ] `EmojiPickerComponent` – Quick reactions (6-8 standard emojis: 👍❤️😂😮😢🔥)
- [ ] Chat state in signals: `messages`, `isLoading`

### Server – Playlist
- [ ] `PlaylistItem` entity (JPA `@Entity`): `id`, `roomId`, `videoUrl`, `title`, `thumbnailUrl`, `duration`, `addedBy`, `position`, `addedAt`
- [ ] WebSocket message handlers: `addToPlaylist(videoUrl)`, `playNow(videoUrl)`, `removeFromPlaylist(itemId)`, `reorderPlaylist(itemId, newPosition)`, `skipToNext()`
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
- [ ] `User` entity (JPA `@Entity`): `id`, `email`, `displayName`, `passwordHash`, `createdAt`
- [ ] Auth endpoints: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`
- [ ] JWT token generation with refresh token rotation (using `jjwt`)
- [ ] Password hashing with BCrypt (`spring-security-crypto`)
- [ ] WebSocket handler supports both anonymous and authenticated users

### Server – Permanent Rooms
- [ ] Extend Room entity: `ownerId` (FK → User, nullable), `isPermanent` (boolean)
- [ ] Anonymous rooms: `expiresAt` = createdAt + 24h
- [ ] Cleanup job (`@Scheduled`): delete expired rooms
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
- [ ] WebSocket message handlers: `sendOffer(targetConnectionId, sdp)`, `sendAnswer(targetConnectionId, sdp)`, `sendIceCandidate(targetConnectionId, candidate)`
- [ ] Use public STUN servers initially (Google)
- [ ] Connection limit: max 6 participants per room for WebRTC (mesh topology)

### Client – WebRTC
- [ ] `WebRtcService` – `RTCPeerConnection` management, media stream handling
- [ ] `getUserMedia()` for camera/microphone access
- [ ] Mesh network: each peer connects to every other peer
- [ ] Signaling via WebSocket (offer/answer/ICE)
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
- [ ] Rate-limiting filter (Spring Boot `Bucket4j` or custom filter)
- [ ] CORS policy for production origin
- [ ] Health checks: DB, WebSocket (`spring-boot-starter-actuator`)
- [ ] SLF4J + Logback: structured logging to stdout (Docker-friendly)
- [ ] Docker Compose production override: Nginx with SSL termination, environment variables
- [ ] Flyway migration on startup (automatic via Spring Boot)

### Verification
- Webcam/mic sharing works between 2+ participants
- Video grid adapts to number of streams
- Mute/camera toggle works
- Full app works end-to-end via `docker compose up`

---

## Overall Verification Matrix

| Check | Command |
|-------|---------|
| Server Build | `./mvnw clean package` (no warnings) |
| Server Tests | `./mvnw test` (all green) |
| Client Build | `ng build` (no errors) |
| Client Lint | `ng lint` (clean) |
| Client Tests | `ng test --watch=false` (all green) |
| Full Stack | `docker compose up` → app at `localhost:4200` |
| E2E Smoke | Create room → join → play video → send chat → verify sync in 2nd tab |