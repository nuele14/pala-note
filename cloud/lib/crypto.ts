import { createCipheriv, createDecipheriv, createHash, randomBytes } from "crypto";

// Secrets at rest (e.g. a user's OpenAI key) are encrypted with AES-256-GCM
// using a key derived from AUTH_SECRET. Format: base64( iv(12) | tag(16) | ct ).

function key(): Buffer {
  const secret = process.env.AUTH_SECRET;
  if (!secret) throw new Error("AUTH_SECRET is not set");
  return createHash("sha256").update(secret).digest();
}

export function encryptSecret(plaintext: string): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key(), iv);
  const ct = Buffer.concat([cipher.update(plaintext, "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, ct]).toString("base64");
}

export function decryptSecret(blob: string): string {
  const buf = Buffer.from(blob, "base64");
  const iv = buf.subarray(0, 12);
  const tag = buf.subarray(12, 28);
  const ct = buf.subarray(28);
  const decipher = createDecipheriv("aes-256-gcm", key(), iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ct), decipher.final()]).toString("utf8");
}

/** Mask a key for display, e.g. "sk-pro…6Xy". */
export function maskSecret(plaintext: string): string {
  if (plaintext.length <= 10) return "•".repeat(plaintext.length);
  return `${plaintext.slice(0, 6)}…${plaintext.slice(-3)}`;
}
