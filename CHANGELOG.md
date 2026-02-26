# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Automatic version bumping on PR merge via GitHub Actions (`version-bump.yml`)
- PR labels (`patch`, `minor`, `major`) control version bump type; defaults to `patch`
- Branch protection documentation in README
- Release workflow for Docker image publishing and GitHub Releases (`release.yml`)

## [0.7.0] - 2026-02-26

### Added

- **Room System** — Create and join rooms via unique shareable links
- **Synchronized YouTube Player** — Play, pause, and seek synced in real-time across all participants
- **Latency Compensation** — Automatic drift correction with gradual catchup (1.05x playback) and hard seek (>5s drift)
- **Live Chat** — Text messages with emoji quick-reactions and unread badge
- **Playlist** — Add videos by URL, play now or queue; drag-and-drop reorder; auto-play next
- **Video Recommendations** — Related video suggestions shown on pause with Play Now / Queue actions
- **WebRTC Video Chat** — Optional webcam and microphone sharing via mesh topology (up to 6 participants)
- **Camera State Broadcasting** — WebSocket-based camera state sync so new joiners see existing streams immediately
- **Authentication** — Optional JWT-based registration and login; anonymous participation with nickname
- **Permanent Rooms** — Registered users can create rooms that persist across server restarts
- **Room Control Modes** — Collaborative (everyone controls) or Host-only mode
- **Collapsible Sidebar** — Chat and playlist in a toggleable sidebar
- **Keyboard Shortcuts** — Space (play/pause), M (mute mic), F (fullscreen)
- **Dark Theme** — Catppuccin Mocha color scheme
- **Docker Support** — Full Docker Compose setup with PostgreSQL, Spring Boot API, and Angular frontend
- **CI Pipeline** — GitHub Actions workflow for build and test on push/PR to main
- **VS Code Developer Workflow** — Preconfigured tasks with dependency chain (DB → Server → Client)
