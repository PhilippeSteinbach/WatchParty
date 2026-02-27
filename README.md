# 🎬 WatchParty

Watch YouTube videos together in real-time with synchronized playback, live chat, and webcam/mic support.

![CI](https://github.com/PhilippeSteinbach/WatchParty/actions/workflows/ci.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)

---

## Features

- 🎬 **Synchronized YouTube Player** – Play, pause, and seek are broadcast to all participants in real time
- 🏠 **Room System** – Create rooms and share them via unique invite links
- 👤 **Anonymous Access** – Join with just a nickname — no account required
- 🔐 **Optional Registration** – Registered users can create permanent rooms
- 🤝 **Control Modes** – Collaborative (everyone controls) or Host Mode (only the host controls)
- 📋 **Playlist** – Add videos by URL — play immediately or append to the playlist
- 💬 **Live Chat** – Text messages with quick reactions (emoji) and unread badge
- 🎥 **Webcam & Microphone** – Optional audio/video via WebRTC (mesh, up to 6 users)
- ⚡ **Latency Compensation** – Automatic drift correction (gradual catch-up / hard seek)
- 📺 **Video Recommendations** – Personalized suggestions while video is paused (play now / add to playlist)
- 📁 **Collapsible Sidebar** – Chat and playlist in a toggleable side panel
- 🛠️ **Developer Workflow** – VS Code tasks with dependency chain (DB → Server → Client) and Stop All task

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Angular (standalone components), TypeScript, SCSS |
| Backend | Java 21+, Spring Boot 3 |
| Database | PostgreSQL 16 |
| Real-time | Spring WebSocket (STOMP over SockJS) |
| Containerisation | Docker Compose |

## Getting Started

### Prerequisites

- Java 21+
- Node.js 24+
- Docker & Docker Compose
- PostgreSQL 16 _(included in Docker Compose)_

### Quick Start — Single Container (easiest)

Everything in one container — no external database needed:

```bash
git clone <repo-url>
cd WatchParty
docker compose -f docker-compose.standalone.yml up
```

| Service | URL |
|---------|-----|
| App | http://localhost:4200 |
| Swagger | http://localhost:4200/swagger-ui.html |

Or run the image directly:

```bash
docker build -f Dockerfile.standalone -t watchparty:standalone .
docker run -p 4200:8080 -v watchparty-data:/data \
  -e JWT_SECRET=my-secret-key \
  watchparty:standalone
```

Data is persisted in the `watchparty-data` volume (H2 file database).

### Quick Start — Multi Container (production)

Uses separate containers for frontend (Nginx), backend (Spring Boot), and database (PostgreSQL):

```bash
git clone <repo-url>
cd WatchParty
JWT_SECRET=my-secret-key docker compose up
```

| Service | URL |
|---------|-----|
| App | http://localhost:4200 |
| API | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |

### Manual Setup (without Docker)

1. **Start PostgreSQL** (or use `docker compose up db`)
2. **Server**
   ```bash
   cd server
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```
3. **Client**
   ```bash
   cd client
   npm install
   npx ng serve
   ```

### VS Code

The project includes preconfigured VS Code tasks with a dependency chain (**DB → Server → Client**) and a **Stop All** task for tearing everything down.

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | Secret key for JWT token signing | _(random, dev only)_ |
| `YOUTUBE_API_KEY` | YouTube Data API v3 key (for recommendations) | — |
| `CORS_ALLOWED_ORIGINS` | Allowed CORS origins | `http://localhost:4200` |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/watchparty` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `watchparty` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `watchparty` |

## Project Structure

```
WatchParty/
├── client/                      # Angular frontend
├── server/                      # Spring Boot backend
├── docker-compose.yml           # Multi-container (Nginx + API + PostgreSQL)
├── docker-compose.standalone.yml # Single container (all-in-one with H2)
├── docker-compose.dev.yml       # Dev DB only
├── Dockerfile.standalone        # All-in-one build
└── README.md
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes
4. Run tests before submitting (`./mvnw test` / `ng test`)
5. Open a pull request

## Branch Protection

When a PR is merged, the [version-bump workflow](.github/workflows/version-bump.yml) automatically increments the version based on PR labels:

| PR Label | Bump | Example |
|----------|------|---------|
| `major` | Major | 0.7.0 → 1.0.0 |
| `minor` | Minor | 0.7.0 → 0.8.0 |
| _(none)_ or `patch` | Patch | 0.7.0 → 0.7.1 |

## License

This project is licensed under the [MIT License](LICENSE).
