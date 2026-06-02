# Pala Cloud

Multi-tenant cloud sync, dashboard, and remote **MCP** server for the **Pala Note**
ESP32-S3 e-paper voice recorder.

A Pala device records audio, transcribes it on-device via OpenAI Whisper, and uploads
the transcript to `POST /api/v1/notes` using a per-device bearer key. Users read their
notes in a Notion-style dashboard, let Claude search/summarize them over a remote MCP
server, route tagged notes to Todoist, and receive outgoing webhooks. The setup wizard
even flashes the firmware from the browser over Web Serial.

Built on Next.js 16 (App Router), React 19, Neon Postgres + Drizzle, Auth.js v5
(GitHub OAuth), Tailwind v4 + shadcn/ui.

## Features

- **Ingest** — `POST /api/v1/notes`, authenticated by a per-device key; idempotent.
- **Dashboard** — notes (search/tag/archive), devices, integrations, developers, settings.
- **Remote MCP** — Claude connects via OAuth 2.1 (self-hosted authorization server with
  DCR + PKCE); tools: `list_notes`, `get_note`, `search_notes`, `list_tags`,
  `get_instructions`.
- **Browser flashing** — `/dashboard/flash` flashes firmware over Web Serial (esptool-js)
  and provisions Wi-Fi / OpenAI key / device key into a config partition.
- **Todoist** — notes matching a trigger tag are turned into structured tasks by an LLM
  pass (the tenant's own OpenAI key) and routed to the right project/section/labels.
- **Webhooks** — signed `note.created` deliveries with durable exponential-backoff retries.

## Architecture

| Piece | Path |
|---|---|
| DB schema (Drizzle) | [db/schema.ts](db/schema.ts) |
| DB client (Neon HTTP) | [db/index.ts](db/index.ts) |
| Auth.js config (GitHub OAuth, tenant-on-signup) | [auth.ts](auth.ts) |
| Key gen/hash + note dedupe | [lib/api-keys.ts](lib/api-keys.ts) |
| Bearer-token → device/tenant resolver | [lib/bearer-auth.ts](lib/bearer-auth.ts) |
| AES-256-GCM secret encryption | [lib/crypto.ts](lib/crypto.ts) |
| Tenant-scoped note queries (dashboard + MCP) | [lib/notes-queries.ts](lib/notes-queries.ts) |
| OAuth 2.1 server (DCR, PKCE, tokens) | [lib/oauth.ts](lib/oauth.ts) |
| Webhook signing + durable retries | [lib/webhooks.ts](lib/webhooks.ts) |
| Todoist routing (LLM task extraction) | [lib/task-ai.ts](lib/task-ai.ts), [lib/todoist.ts](lib/todoist.ts) |
| **Ingest endpoint** | [app/api/v1/notes/route.ts](app/api/v1/notes/route.ts) |
| **Remote MCP server** | [app/[transport]/route.ts](app/[transport]/route.ts) |

### Credentials model
- **Devices** ([db/schema.ts](db/schema.ts) `devices`) — one row per physical Pala, with
  its own ingest key. Keys are `pala_` + base62; only the **SHA-256 hash** is stored and
  the plaintext is shown once. Rotating a device regenerates its key in place.
- **MCP** — Claude authenticates over **OAuth 2.1**, not static keys. The app is its own
  authorization server (`.well-known/oauth-authorization-server`, `/api/oauth/*`).
- **Per-tenant secrets** (OpenAI key, Wi-Fi, Todoist token) are encrypted at rest with
  AES-256-GCM ([lib/crypto.ts](lib/crypto.ts)).

### Idempotent ingest
Each note's `dedupe_key = sha256(device_id:note_num:created_utc)` with a
`UNIQUE(tenant_id, dedupe_key)` constraint, so re-syncing never duplicates rows
(`INSERT … ON CONFLICT DO NOTHING`).

## Local setup

1. **Provision a Neon Postgres database** at https://neon.tech and copy its connection string.
2. **Create a GitHub OAuth app** (https://github.com/settings/developers) with callback
   URL `http://localhost:3000/api/auth/callback/github`.
3. `cp .env.example .env.local` and fill in `DATABASE_URL`, `AUTH_SECRET`
   (`openssl rand -base64 32`), `AUTH_GITHUB_ID`, `AUTH_GITHUB_SECRET`, `CRON_SECRET`.
4. Install, push the schema, and run:
   ```bash
   npm install
   npm run db:push      # creates tables in Neon
   npm run dev
   ```

> Note: the deployed reference app's GitHub OAuth callback is production-only, so login
> there can't be completed from localhost — use your own OAuth app for local dev.

## Deploy to Vercel

1. Push this repo to GitHub and import it in Vercel. **Set the project's Root Directory to
   `cloud`** — the Next.js app lives there in this monorepo.
2. Set env vars: `DATABASE_URL`, `AUTH_SECRET`, `AUTH_GITHUB_ID`, `AUTH_GITHUB_SECRET`,
   `CRON_SECRET`. Point the GitHub OAuth callback at your production URL.
3. Apply the schema against prod (`npm run db:push` with the prod `DATABASE_URL`).
4. `vercel.json` registers the daily webhook-retry cron at `/api/cron/webhooks`.

## Firmware

The device firmware (Arduino/ESP32-S3) lives in the sibling [`../firmware/pala_note`](../firmware/pala_note)
directory of this monorepo. The browser flasher reads a `*.merged.bin` placed in
`public/firmware/` (git-ignored); see [public/firmware/README.md](public/firmware/README.md).
Build + stage it headlessly with [scripts/build-firmware.sh](scripts/build-firmware.sh).
Any published binary must be built with the firmware's `secrets.h` scrubbed to `"...."`
placeholders — the flasher provisions real credentials into the config partition at flash
time (the build script refuses to build otherwise).

## Security notes

- MCP is OAuth-only: the `/api/oauth/authorize` consent screen uses an HMAC CSRF token,
  DCR restricts redirect URIs to https/localhost, and PKCE is S256-only.
- Firmware pins Google Trust Services CA roots for TLS to api.openai.com and the ingest
  host (no `setInsecure()`).
- Rate limiting ([lib/rate-limit.ts](lib/rate-limit.ts)) is best-effort in-memory
  (per-instance); a shared store (KV/Redis) is the next step for robust limiting.
- `AUTH_SECRET` currently derives the encryption key; a dedicated
  `SECRETS_ENCRYPTION_KEY` + HKDF is a planned hardening step.
