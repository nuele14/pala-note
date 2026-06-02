import {
  pgTable,
  text,
  timestamp,
  integer,
  uuid,
  json,
  boolean,
  primaryKey,
  uniqueIndex,
  index,
} from "drizzle-orm/pg-core";
import type { AdapterAccountType } from "next-auth/adapters";

// ---------------------------------------------------------------------------
// Application tables
// ---------------------------------------------------------------------------

export const tenants = pgTable("tenants", {
  id: uuid("id").primaryKey().defaultRandom(),
  name: text("name").notNull(),
  // Persistent AI context for this workspace's notes. Surfaced to Claude via
  // the MCP `get_instructions` tool so the assistant knows how to treat them.
  instructions: text("instructions").notNull().default(""),
  // IANA timezone for displaying note timestamps (default Pacific).
  timezone: text("timezone").notNull().default("America/Los_Angeles"),
  // The user's OpenAI key (for on-device Whisper), AES-256-GCM encrypted.
  // Stored so we can re-flash a device without re-entering it. Null if unset.
  openaiKeyEnc: text("openai_key_enc"),
  // Saved Wi-Fi for the flasher, AES-256-GCM encrypted (SSID isn't secret but
  // we keep them together). Null if the user hasn't opted to remember it.
  // Up to two networks (primary + optional secondary, baked at flash time).
  wifiSsidEnc: text("wifi_ssid_enc"),
  wifiPassEnc: text("wifi_pass_enc"),
  wifiSsid2Enc: text("wifi_ssid2_enc"),
  wifiPass2Enc: text("wifi_pass2_enc"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

// Physical Pala devices. Each owns its ingest credential (key hash/prefix live
// here) plus identity it reports (device_id), firmware, and flash/sync times.
export const devices = pgTable(
  "devices",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    tenantId: uuid("tenant_id")
      .notNull()
      .references(() => tenants.id, { onDelete: "cascade" }),
    name: text("name").notNull(),
    keyPrefix: text("key_prefix").notNull(), // first 8 chars, for display
    keyHash: text("key_hash").notNull(), // sha256(full ingest key), hex
    deviceId: text("device_id"), // self-reported ESP32 id (learned on ingest)
    firmwareVersion: text("firmware_version"),
    lastFlashedAt: timestamp("last_flashed_at", { withTimezone: true }),
    lastSeenAt: timestamp("last_seen_at", { withTimezone: true }), // last upload
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    revokedAt: timestamp("revoked_at", { withTimezone: true }),
  },
  (t) => [uniqueIndex("devices_key_hash_idx").on(t.keyHash)],
);

// Voice-note transcripts uploaded from devices.
export const notes = pgTable(
  "notes",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    tenantId: uuid("tenant_id")
      .notNull()
      .references(() => tenants.id, { onDelete: "cascade" }),
    deviceNoteNum: integer("device_note_num").notNull(),
    text: text("text").notNull(),
    tag: text("tag"),
    createdUtc: timestamp("created_utc", { withTimezone: true }),
    dedupeKey: text("dedupe_key").notNull(),
    uploadedAt: timestamp("uploaded_at", { withTimezone: true }).notNull().defaultNow(),
    // Set once a note has been turned into a Todoist task (idempotency + UI).
    todoistTaskId: text("todoist_task_id"),
    // Soft archive: hidden from the default list + MCP when set.
    archivedAt: timestamp("archived_at", { withTimezone: true }),
  },
  (t) => [
    uniqueIndex("notes_tenant_dedupe_idx").on(t.tenantId, t.dedupeKey),
    index("notes_tenant_created_idx").on(t.tenantId, t.createdUtc),
    index("notes_tenant_devicenum_idx").on(t.tenantId, t.deviceNoteNum),
  ],
);

// Per-tenant Todoist integration. Notes tagged with `todoTag` are turned into
// Todoist tasks by an LLM that's given the user's projects/labels + prompts.
export const todoistConfig = pgTable("todoist_config", {
  tenantId: uuid("tenant_id")
    .primaryKey()
    .references(() => tenants.id, { onDelete: "cascade" }),
  apiTokenEnc: text("api_token_enc").notNull(), // AES-256-GCM
  // Device tag that marks a note as a task (matches the firmware note tag).
  todoTag: text("todo_tag").notNull().default("todo"),
  // Extra user guidance for categorization/summarization.
  customPrompt: text("custom_prompt").notNull().default(""),
  projects: json("projects").$type<{ id: string; name: string }[]>().notNull().default([]),
  sections: json("sections")
    .$type<{ id: string; name: string; projectId: string }[]>()
    .notNull()
    .default([]),
  labels: json("labels").$type<string[]>().notNull().default([]),
  syncedAt: timestamp("synced_at", { withTimezone: true }),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

// Outgoing webhooks: the user registers endpoints we POST events to (e.g.
// note.created) with an HMAC-SHA256 signature. The `secret` is a signing key
// the developer uses to verify deliveries.
export const webhooks = pgTable(
  "webhooks",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    tenantId: uuid("tenant_id")
      .notNull()
      .references(() => tenants.id, { onDelete: "cascade" }),
    url: text("url").notNull(),
    secret: text("secret").notNull(),
    events: json("events").$type<string[]>().notNull().default(["note.created"]),
    enabled: boolean("enabled").notNull().default(true),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    lastStatus: integer("last_status"),
    lastDeliveryAt: timestamp("last_delivery_at", { withTimezone: true }),
    lastError: text("last_error"),
  },
  (t) => [index("webhooks_tenant_idx").on(t.tenantId)],
);

// One row per webhook event delivery. Retried with exponential backoff by the
// cron worker until it succeeds or attempts are exhausted.
export const webhookDeliveries = pgTable(
  "webhook_deliveries",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    webhookId: uuid("webhook_id")
      .notNull()
      .references(() => webhooks.id, { onDelete: "cascade" }),
    tenantId: uuid("tenant_id")
      .notNull()
      .references(() => tenants.id, { onDelete: "cascade" }),
    eventType: text("event_type").notNull(),
    body: text("body").notNull(), // exact JSON payload (signed fresh per attempt)
    status: text("status", { enum: ["pending", "succeeded", "failed"] })
      .notNull()
      .default("pending"),
    attempts: integer("attempts").notNull().default(0),
    nextAttemptAt: timestamp("next_attempt_at", { withTimezone: true }).notNull().defaultNow(),
    lastStatus: integer("last_status"),
    lastError: text("last_error"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    deliveredAt: timestamp("delivered_at", { withTimezone: true }),
  },
  (t) => [index("webhook_deliveries_due_idx").on(t.status, t.nextAttemptAt)],
);

// ---------------------------------------------------------------------------
// OAuth 2.1 authorization server — lets MCP clients (e.g. Claude) connect via
// Dynamic Client Registration + authorization-code/PKCE instead of a pasted
// API key. We are our own auth server; the human login step reuses Auth.js.
// ---------------------------------------------------------------------------

export const oauthClients = pgTable("oauth_clients", {
  clientId: text("client_id").primaryKey(),
  // Null for public (PKCE) clients using token_endpoint_auth_method "none".
  clientSecretHash: text("client_secret_hash"),
  clientName: text("client_name").notNull(),
  redirectUris: json("redirect_uris").$type<string[]>().notNull(),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

export const oauthAuthCodes = pgTable("oauth_auth_codes", {
  codeHash: text("code_hash").primaryKey(),
  clientId: text("client_id").notNull(),
  tenantId: uuid("tenant_id")
    .notNull()
    .references(() => tenants.id, { onDelete: "cascade" }),
  redirectUri: text("redirect_uri").notNull(),
  codeChallenge: text("code_challenge").notNull(),
  codeChallengeMethod: text("code_challenge_method").notNull(),
  scope: text("scope").notNull(),
  expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
});

export const oauthTokens = pgTable(
  "oauth_tokens",
  {
    tokenHash: text("token_hash").primaryKey(),
    kind: text("kind", { enum: ["access", "refresh"] }).notNull(),
    clientId: text("client_id").notNull(),
    tenantId: uuid("tenant_id")
      .notNull()
      .references(() => tenants.id, { onDelete: "cascade" }),
    scope: text("scope").notNull(),
    expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [index("oauth_tokens_tenant_idx").on(t.tenantId)],
);

// ---------------------------------------------------------------------------
// Auth.js (NextAuth) tables — schema expected by @auth/drizzle-adapter.
// `users.tenantId` is our extension linking a user to its tenant.
// ---------------------------------------------------------------------------

export const users = pgTable("user", {
  id: text("id")
    .primaryKey()
    .$defaultFn(() => crypto.randomUUID()),
  name: text("name"),
  email: text("email").unique(),
  emailVerified: timestamp("emailVerified", { mode: "date" }),
  image: text("image"),
  tenantId: uuid("tenant_id").references(() => tenants.id, { onDelete: "set null" }),
});

export const accounts = pgTable(
  "account",
  {
    userId: text("userId")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    type: text("type").$type<AdapterAccountType>().notNull(),
    provider: text("provider").notNull(),
    providerAccountId: text("providerAccountId").notNull(),
    refresh_token: text("refresh_token"),
    access_token: text("access_token"),
    expires_at: integer("expires_at"),
    token_type: text("token_type"),
    scope: text("scope"),
    id_token: text("id_token"),
    session_state: text("session_state"),
  },
  (account) => [
    primaryKey({ columns: [account.provider, account.providerAccountId] }),
  ],
);

export const sessions = pgTable("session", {
  sessionToken: text("sessionToken").primaryKey(),
  userId: text("userId")
    .notNull()
    .references(() => users.id, { onDelete: "cascade" }),
  expires: timestamp("expires", { mode: "date" }).notNull(),
});

export const verificationTokens = pgTable(
  "verificationToken",
  {
    identifier: text("identifier").notNull(),
    token: text("token").notNull(),
    expires: timestamp("expires", { mode: "date" }).notNull(),
  },
  (vt) => [primaryKey({ columns: [vt.identifier, vt.token] })],
);
