import { and, eq, isNull } from "drizzle-orm";
import { db } from "@/db";
import { devices } from "@/db/schema";
import { hashApiKey } from "@/lib/api-keys";

/** Extract a bearer token from an Authorization header value. */
export function parseBearer(header: string | null): string | null {
  if (!header) return null;
  const match = /^Bearer\s+(.+)$/i.exec(header.trim());
  return match ? match[1].trim() : null;
}

/**
 * Resolve a device ingest token to its device + tenant. Returns null if unknown
 * or revoked. The ingest route updates last_seen_at / device_id itself.
 */
export async function resolveDeviceKey(
  plaintext: string | null,
): Promise<{ deviceId: string; tenantId: string } | null> {
  if (!plaintext) return null;
  const hash = hashApiKey(plaintext);
  const [row] = await db
    .select({ id: devices.id, tenantId: devices.tenantId })
    .from(devices)
    .where(and(eq(devices.keyHash, hash), isNull(devices.revokedAt)))
    .limit(1);
  if (!row) return null;
  return { deviceId: row.id, tenantId: row.tenantId };
}
