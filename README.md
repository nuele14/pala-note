# Pala Note

An ESP32-S3 e-paper voice recorder and its cloud. Press a button, speak, and the device
transcribes on-device (OpenAI Whisper) and syncs the transcript to the cloud — where you
read it in a dashboard, let Claude search it over MCP, and route tagged notes to Todoist.

This is a monorepo with three components:

| Directory | What it is |
|---|---|
| [`cloud/`](cloud) | Next.js 16 + Neon Postgres app: ingest API, dashboard, remote MCP server, Todoist + webhooks, and a browser-based firmware flasher. See [cloud/README.md](cloud/README.md). |
| [`firmware/pala_note/`](firmware/pala_note) | The ESP32-S3 Arduino firmware (recording, e-paper UI, Wi-Fi, on-device transcription, cloud sync). See [firmware/pala_note/BUILD.md](firmware/pala_note/BUILD.md). |
| [`hardware/`](hardware) | 3D-printable case (STEP + 3MF). |

## How the pieces fit

```
 ┌────────────┐  record + Whisper   ┌──────────────────┐   MCP / dashboard   ┌────────┐
 │  Pala Note │ ──────────────────▶ │   Pala Cloud     │ ◀────────────────── │ Claude │
 │ (firmware) │  POST /api/v1/notes │ (cloud/, Vercel) │   webhooks/Todoist  │  + you │
 └────────────┘                     └──────────────────┘                     └────────┘
```

The cloud's setup wizard flashes the firmware over Web Serial and provisions Wi-Fi, the
OpenAI key, and a per-device ingest key into a config partition — so no real credentials
are ever baked into a published binary.

## Getting started

- **Run the cloud locally:** see [cloud/README.md](cloud/README.md).
- **Build the firmware:** [firmware/pala_note/BUILD.md](firmware/pala_note/BUILD.md), or run
  `cloud/scripts/build-firmware.sh` to compile + stage it for the browser flasher.

## A note on secrets

`secrets.h` (firmware) and `.env.local` (cloud) are git-ignored and must never be
committed. Credentials are provisioned at flash/runtime, not stored in the repo.
See each component's README for the env/config it needs.
