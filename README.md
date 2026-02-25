# WatchParty – Watch Videos Together

Synchrones YouTube-Schauen in virtuellen Räumen mit Echtzeit-Chat, Webcam/Mikrofon und kollaborativer oder Host-basierter Steuerung.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Angular (standalone components), TypeScript, SCSS |
| Backend | Java 21+, Spring Boot 3 |
| Database | PostgreSQL 16 |
| Real-time | Spring WebSocket (STOMP over SockJS) |
| Containerisation | Docker Compose |

## Features

- 🎬 **Synchroner YouTube Player** – Play, Pause, Seek werden in Echtzeit an alle Teilnehmer übertragen.
- 🏠 **Room-System** – Räume erstellen und via Unique Link teilen
- 👤 **Anonyme Teilnahme** – Nickname beim Beitritt vergeben (kein Account nötig)
- 🔐 **Optionale Registrierung** – Registrierte User können permanente Räume erstellen
- 🤝 **Steuerungsmodi** – Collaborative (jeder steuert) oder Host-Mode (nur Host steuert)
- 📋 **Playlist** – Videos via URL hinzufügen, "Sofort abspielen" oder "An Playlist anhängen"
- 💬 **Live-Chat** – Text-Nachrichten mit Quick-Reactions (Emoji) und Unread-Badge
- 🎥 **Webcam & Mikrofon** – Optionales Audio/Video via WebRTC (Mesh, bis 6 User)
- ⚡ **Latenz-Kompensation** – Automatische Drift-Korrektur (gradual catchup / hard-seek)
- 📺 **Video-Empfehlungen** – Eigene Empfehlungen bei pausiertem Video (Play Now / zur Playlist hinzufügen)
- 📁 **Einklappbare Sidebar** – Chat & Playlist in ein-/ausklappbarer Seitenleiste
- 🛠️ **Developer Workflow** – VS Code Tasks mit Dependency-Chain (DB → Server → Client) und Stop-All Task
