import { createHash, randomBytes } from "crypto";

const BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

/** Encode bytes as a base62 string. */
function toBase62(buf: Buffer): string {
  let out = "";
  for (const byte of buf) {
    out += BASE62[byte % 62];
  }
  return out;
}

export interface GeneratedKey {
  /** Full plaintext key — shown to the user exactly once, never stored. */
  plaintext: string;
  /** First 8 chars, safe to store/display for identification. */
  prefix: string;
  /** SHA-256 hex hash of the full key — what we store. */
  hash: string;
}

/** Generate a new API key: `pala_` + 32 random base62 chars. */
export function generateApiKey(): GeneratedKey {
  const body = toBase62(randomBytes(32));
  const plaintext = `pala_${body}`;
  return {
    plaintext,
    prefix: plaintext.slice(0, 8),
    hash: hashApiKey(plaintext),
  };
}

/** SHA-256 hex hash of a key. Keys are high-entropy, so a fast hash is correct. */
export function hashApiKey(plaintext: string): string {
  return createHash("sha256").update(plaintext).digest("hex");
}

/** Stable dedupe key for a note: sha256(device_id:note_num:created_utc). */
export function noteDedupeKey(
  deviceId: string,
  noteNum: number,
  createdUtc: string | null,
): string {
  return createHash("sha256")
    .update(`${deviceId}:${noteNum}:${createdUtc ?? ""}`)
    .digest("hex");
}
