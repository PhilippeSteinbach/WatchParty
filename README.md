# 🎬 WatchParty

Watch YouTube videos together in real-time — self-hostable with a single Docker command.

![CI](https://github.com/PhilippeSteinbach/WatchParty/actions/workflows/ci.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)

---

## About

WatchParty was built as an exercise in **agentic development** (sometimes called *vibe coding*) — writing software with heavy AI assistance to explore how far that workflow can go.
The idea came from a simple real-life use case: a group of friends regularly watching videos together online using a well-known third-party service.
Instead of continuing to rely on that, WatchParty became the playground to learn AI-assisted development hands-on.

## Core Features

- 🎬 **Synchronized Playback** – Play, pause, and seek are broadcast to all participants in real time
- 💬 **Live Chat** – Text messages with emoji reactions while watching together
- 🏠 **Room System** – Create rooms and share invite links — join anonymously or with an account
- 🎥 **Webcam & Microphone** – Optional audio/video so you can see and hear each other

## Self-Hosting

### Docker One-Liner

```bash
docker run -d -p 4200:8080 -v watchparty-data:/data \
  -e JWT_SECRET=change-me \
  ghcr.io/philippesteinbach/watchparty:latest
```

Open **http://localhost:4200** and you're ready to go.

### Docker Compose

```yaml
# docker-compose.standalone.yml
services:
  watchparty:
    image: ghcr.io/philippesteinbach/watchparty:latest
    ports:
      - "4200:8080"
    environment:
      - JWT_SECRET=change-me
      - YOUTUBE_API_KEY=           # optional – enables video recommendations
    volumes:
      - watchparty-data:/data

volumes:
  watchparty-data:
```

```bash
docker compose -f docker-compose.standalone.yml up -d
```

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `JWT_SECRET` | Secret key for JWT token signing | Yes |
| `YOUTUBE_API_KEY` | YouTube Data API v3 key (enables video recommendations) | No |

Data is persisted in the `watchparty-data` volume (embedded H2 database).

## License

This project is licensed under the [MIT License](LICENSE).
