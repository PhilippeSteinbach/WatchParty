# 🎬 WatchParty

Watch YouTube videos together in real-time — self-hostable with a single Docker command.

![CI](https://github.com/PhilippeSteinbach/WatchParty/actions/workflows/ci.yml/badge.svg)
![Version](https://img.shields.io/github/v/tag/PhilippeSteinbach/WatchParty?label=version)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)

---

## About

WatchParty was built as an exercise in **agentic development** (mostly called *vibe coding*) — writing software with AI assistance to explore how far that workflow can go.
The idea came from a simple real-life use case: a group of friends regularly watching videos together online using a well-known third-party service.
Instead of continuing to rely on that, WatchParty became the playground to learn AI-assisted development hands-on.

## Core Features

- 🎬 **Synchronized Playback** – Play, pause, and seek are broadcast to all participants in real time
- 💬 **Live Chat** – Text messages with emoji reactions while watching together
- 🏠 **Room System** – Create rooms and share invite links — join anonymously or with an account
- 🎥 **Webcam & Microphone** – Optional audio/video so you can see and hear each other

## Self-Hosting

Run this single command in your terminal — no cloning, no setup:

```bash
docker run -d -p 8080:8080 -v watchparty-data:/data \
  -e JWT_SECRET=change-me \
  -e YOUTUBE_API_KEY=your-api-key \
  ghcr.io/philippesteinbach/watchparty:latest
```

Or use Docker Compose:

```yaml
services:
  app:
    image: ghcr.io/philippesteinbach/watchparty:latest
    ports:
      - "8080:8080"
    volumes:
      - watchparty-data:/data
    environment:
      JWT_SECRET: ${JWT_SECRET:-change-me-in-production}
      YOUTUBE_API_KEY: ${YOUTUBE_API_KEY:-}
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 3

volumes:
  watchparty-data:
```

Open **http://localhost:8080** and you're ready to go.

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `JWT_SECRET` | Secret key for JWT token signing | Yes |
| `YOUTUBE_API_KEY` | YouTube Data API v3 key (enables video recommendations) | No |

Data is persisted in the `watchparty-data` volume (embedded H2 database).

## License

This project is licensed under the [MIT License](LICENSE).
