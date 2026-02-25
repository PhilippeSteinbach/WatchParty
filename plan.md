# WatchParty – Implementation Plan

Phasenweise Umsetzung: MVP zuerst lauffähig, dann iterativ erweitern.

---

## Phase 0: Project Scaffolding & Infrastructure ✅

**Goal:** Runnable project structure with build, test, and Docker.

### Server
- [x] Create Spring Boot 3 project with Maven in `server/` (Java 21+)
- [x] Add dependencies in `pom.xml`:
  - `spring-boot-starter-web`
  - `spring-boot-starter-data-jpa`
  - `spring-boot-starter-websocket`
  - `spring-boot-starter-validation`
  - `spring-boot-starter-security`
  - `springdoc-openapi-starter-webmvc-ui`
  - `postgresql` driver
  - `flyway-core`
  - `jjwt` (io.jsonwebtoken)
- [x] Configure `WatchPartyApplication.java` main class
- [x] Create `application.yml` + `application-dev.yml` (PostgreSQL connection, JWT settings, YouTube API key placeholder via environment variables)
- [x] Add `Dockerfile` + `.dockerignore`

### Client
- [x] Scaffold Angular project in `client/`: `ng new watch-party --standalone --style=scss --routing`
- [x] Enable strict mode in `tsconfig.json`
- [x] Install dependencies: `@stomp/stompjs`, `sockjs-client`, `@types/youtube`
- [x] Create `proxy.conf.json` for API calls in development
- [x] Add `Dockerfile` (multi-stage: Node build → Nginx)

### Infrastructure
- [x] Create `docker-compose.yml` in repo root with services: `db` (PostgreSQL 16), `api` (Spring Boot), `client` (Nginx)
- [x] Configure volumes for DB persistence, ports: 4200, 8080, 5432
- [x] Add CI workflow in `.github/workflows/`: build + test for both projects

### Verification
- `docker compose up` starts all 3 services
- Swagger UI at `localhost:8080/swagger-ui.html`
- Angular app at `localhost:4200`

---

## Phase 1: MVP – Room System & Synchronized YouTube Player ✅

**Goal:** Create rooms, join via link, watch YouTube videos synchronously. Anonymous participation with nickname.

### Server – Domain & Data
- [x] Create `Room` entity (JPA `@Entity`): `id` (UUID), `code` (String, 6-8 chars, unique), `name`, `controlMode` (enum: COLLABORATIVE | HOST_ONLY), `hostConnectionId`, `currentVideoUrl`, `currentTime` (Duration), `isPlaying`, `createdAt`, `expiresAt`
- [x] Create `Participant` entity: `id`, `roomId`, `nickname`, `connectionId`, `isHost`, `joinedAt`
- [x] Create `RoomRepository` and `ParticipantRepository` (Spring Data JPA)
- [x] Create initial Flyway migration

### Server – Room API (REST Controllers)
- [x] `POST /api/rooms` → Create room (Name, ControlMode), return room code
- [x] `GET /api/rooms/{code}` → Get room details
- [x] `DELETE /api/rooms/{code}` → Close room (host only)
- [x] Jakarta Bean Validation: room name required, max 100 chars
- [x] Problem Details responses for errors (RFC 9457)

### Server – WebSocket Handler (`WatchPartyWebSocketHandler`)
- [x] `joinRoom(roomCode, nickname)` → Add participant to session group, validate nickname, return current player state
- [x] `leaveRoom()` → Remove participant, on host leave: assign new host or close room
- [x] `play()` / `pause()` / `seek(timeSeconds)` → Check permissions (ControlMode), broadcast to group
- [x] `changeVideo(videoUrl)` → Change video, broadcast
- [x] `syncState()` → Periodic state sync (heartbeat every 5s)
- [x] Handle session disconnect for cleanup

### Client – Room Creation & Joining
- [x] `RoomService` – HTTP calls to Room API, room state as signals
- [x] `WebSocketService` – Manage STOMP/SockJS connection, expose events as signals
- [x] `HomeComponent` – Create room (name, control mode), result: shareable link
- [x] `JoinRoomComponent` – Enter nickname, join room (route: `/room/:code`)
- [x] Routing: `/` → Home, `/room/:code` → WatchRoom
- [x] Model interfaces: `Room`, `Participant`, `PlayerState`, `RoomSettings`

### Client – YouTube Player & Sync
- [x] `WatchRoomComponent` – Main container with player, participant list
- [x] `YoutubePlayerComponent` – YouTube IFrame API integration
- [x] Forward player events (`onStateChange`) to WebSocket
- [x] Apply incoming WebSocket events to player (play/pause/seek)
- [x] Respect control mode: disable controls for non-hosts in Host-Mode
- [x] `ParticipantListComponent` – Show participants, mark host

### Latency Compensation (Basic)
- [x] Client sends current playback position to server periodically
- [x] Server compares positions; drift > 2s → `seek` to drifting client
- [x] Gradual catchup: playback rate 1.05x for small drift (< 2s)
- [x] Hard seek: drift > 5s → jump to correct position immediately

### Verification
- Two browser tabs can join a room
- Play/pause/seek from one user is reflected live in the other
- Drift correction works measurably

---

## Phase 2: Live Chat & Playlist ✅

**Goal:** Text chat with emoji reactions and video playlist.

### Server – Chat
- [x] `ChatMessage` entity (JPA `@Entity`): `id`, `roomId`, `nickname`, `content`, `reactions` (JSON column via `@JdbcTypeCode`), `sentAt`
- [x] WebSocket message handlers: `sendMessage(content)`, `addReaction(messageId, emoji)`
- [x] Persist messages in DB (last 200 per room), load history on join
- [x] Validation: max 500 chars, rate limiting (max 5 messages/10s per user)

### Client – Chat UI
- [x] `ChatPanelComponent` – Message list with auto-scroll, input field
- [x] `ChatMessageComponent` – Single message with nickname, timestamp, reaction buttons
- [x] `EmojiPickerComponent` – Quick reactions (6-8 standard emojis: 👍❤️😂😮😢🔥)
- [x] Chat state in signals: `messages`, `isLoading`

### Server – Playlist
- [x] `PlaylistItem` entity (JPA `@Entity`): `id`, `roomId`, `videoUrl`, `title`, `thumbnailUrl`, `duration`, `addedBy`, `position`, `addedAt`
- [x] WebSocket message handlers: `addToPlaylist(videoUrl)`, `playNow(videoUrl)`, `removeFromPlaylist(itemId)`, `reorderPlaylist(itemId, newPosition)`, `skipToNext()`
- [x] `YouTubeService` – Fetch video metadata (title, thumbnail, duration) from YouTube Data API v3
- [x] Auto-play next video when current one ends

### Client – Playlist UI
- [x] `PlaylistPanelComponent` – Video list with drag & drop reorder
- [x] Add video form with "Add to Queue" / "Play Now" buttons
- [x] `PlaylistService` – Signal-based playlist state
- [x] Angular CDK `DragDropModule` for reorder

### Bug Fixes (discovered during Phase 2)
- [x] Fix `/room.sync` endpoint: removed erroneous `@Payload PlayerStateMessage` parameter (client sends empty body)
- [x] Fix `/room.playlist.next` endpoint: server now derives current position from room state instead of expecting client payload
- [x] Video player fills designated space (added `:host` sizing to `YoutubePlayerComponent`)
- [x] Browser auto-opens on `ng serve` (`angular.json` → `"open": true`)

### Verification
- Chat messages appear live for all participants
- Emoji reactions are synchronized
- Videos can be added to playlist and reordered
- "Play Now" replaces the current video for all

---

## Phase 2.5: UX Improvements & Developer Experience ✅

**Goal:** Quality-of-life improvements for the UI (sidebar, chat, overlay) and developer workflow (VS Code tasks).

### Developer Workflow
- [x] `stop-all` VS Code task – kills processes on ports 4200/8080 and runs `docker compose down`
- [x] Task dependency chain: `db-start` → `server-run` → `client-serve` (frontend waits for backend)
- [x] Background tasks with problem matchers for automatic readiness detection
- [x] `presentation.close: true` on background tasks to auto-close terminals on stop
- [x] Fix `server-run` profile activation: use `SPRING_PROFILES_ACTIVE` env var instead of Maven `-D` flag

### Server – Recommendations & Fixes
- [x] Fix `WatchPartyWebSocketHandler` compilation error: inject missing `PlaylistItemRepository`
- [x] Fix `YouTubeService.fetchMetadata()`: always generate thumbnail URL from video ID (no API key needed)
- [x] `VideoRecommendation` DTO for recommendation responses
- [x] `VideoController` – `GET /api/videos/{videoId}/recommendations` endpoint
- [x] `YouTubeService.searchRelated()` – search YouTube by current video title for related content

### Client – UI Enhancements
- [x] **Unread chat badge** – pink circle with unread message count on the chat tab button
- [x] **Collapsible sidebar** – toggle arrow in the tab-bar row, collapses/expands the sidebar
- [x] **Pause overlay** – fully opaque overlay (`#11111b`) hides YouTube's unclickable recommendations
- [x] **Custom recommendations** – grid of related video cards with "Play Now" / "Queue" buttons when paused
- [x] **Thumbnail fallback** – client-side `thumbnailFor()` extracts video ID and constructs thumbnail URL
- [x] **Optimistic play/pause** – local `isPlaying` signal for instant overlay response (no server round-trip wait)
- [x] **Recommendation autoplay** – "Play Now" sends CHANGE_VIDEO + delayed PLAY action to start video immediately

### Verification
- Sidebar collapses/expands without overlapping other UI elements
- Unread badge appears and clears correctly when switching tabs
- Pause overlay shows custom recommendations and hides YouTube's
- Clicking play or recommendation dismisses overlay immediately
- Thumbnails display in playlist without API key

---

## Phase 3: Authentication & Persistence ✅

**Goal:** Optional registration, permanent rooms, JWT auth.

### Server – Auth
- [x] `User` entity (JPA `@Entity`): `id`, `email`, `displayName`, `passwordHash`, `createdAt`
- [x] Auth endpoints: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`
- [x] JWT token generation with refresh token rotation (using `jjwt`)
- [x] Password hashing with BCrypt (`spring-security-crypto`)
- [x] WebSocket handler supports both anonymous and authenticated users

### Server – Permanent Rooms
- [x] Extend Room entity: `ownerId` (FK → User, nullable), `isPermanent` (boolean)
- [x] Anonymous rooms: `expiresAt` = createdAt + 24h
- [x] Cleanup job (`@Scheduled`): delete expired rooms
- [x] Registered users: can create permanent rooms (no ExpiresAt)
- [x] `GET /api/users/me/rooms` → List own rooms

### Client – Auth UI
- [x] `LoginComponent`, `RegisterComponent` – Reactive forms with validation
- [x] `AuthService` – JWT in localStorage, auth state as signal (`currentUser`)
- [x] `AuthInterceptor` – Attach Bearer token to API requests
- [x] `AuthGuard` – Protected routes (e.g., "My Rooms")
- [x] `UserMenuComponent` – Login/logout, nickname display
- [x] Route: `/my-rooms` → List of own permanent rooms

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