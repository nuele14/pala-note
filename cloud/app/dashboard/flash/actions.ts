"use server";

import { and, desc, eq, isNull } from "drizzle-orm";
import { auth } from "@/auth";
import { db } from "@/db";
import { devices, tenants } from "@/db/schema";
import { generateApiKey } from "@/lib/api-keys";
import { encryptSecret, decryptSecret, maskSecret } from "@/lib/crypto";

async function requireTenant(): Promise<string> {
  const session = await auth();
  const tenantId = session?.user?.tenantId;
  if (!tenantId) throw new Error("Not authenticated");
  return tenantId;
}

/** Verify an OpenAI key by listing models, then store it encrypted. */
export async function verifyAndSaveOpenAiKey(
  apiKey: string,
): Promise<{ ok: boolean; error?: string }> {
  const tenantId = await requireTenant();
  const key = apiKey.trim();
  if (!key.startsWith("sk-")) {
    return { ok: false, error: "That doesn't look like an OpenAI key (should start with sk-)." };
  }

  try {
    const res = await fetch("https://api.openai.com/v1/models", {
      headers: { Authorization: `Bearer ${key}` },
      // Don't cache; we want a live check.
      cache: "no-store",
    });
    if (res.status === 401) {
      return { ok: false, error: "OpenAI rejected this key (401). Check it and try again." };
    }
    if (!res.ok) {
      return { ok: false, error: `OpenAI returned ${res.status}. Try again in a moment.` };
    }
  } catch {
    return { ok: false, error: "Couldn't reach OpenAI to verify the key." };
  }

  await db
    .update(tenants)
    .set({ openaiKeyEnc: encryptSecret(key) })
    .where(eq(tenants.id, tenantId));
  return { ok: true };
}

/** A masked preview of the stored OpenAI key, or null if none is stored. */
export async function getStoredOpenAiKeyMask(): Promise<string | null> {
  const tenantId = await requireTenant();
  const [row] = await db
    .select({ enc: tenants.openaiKeyEnc })
    .from(tenants)
    .where(eq(tenants.id, tenantId))
    .limit(1);
  if (!row?.enc) return null;
  try {
    return maskSecret(decryptSecret(row.enc));
  } catch {
    return null;
  }
}

/**
 * The full stored OpenAI key, for baking into the device config during flashing.
 * Returned only to the authenticated owner's browser. Null if none stored.
 */
export async function getStoredOpenAiKey(): Promise<string | null> {
  const tenantId = await requireTenant();
  const [row] = await db
    .select({ enc: tenants.openaiKeyEnc })
    .from(tenants)
    .where(eq(tenants.id, tenantId))
    .limit(1);
  if (!row?.enc) return null;
  try {
    return decryptSecret(row.enc);
  } catch {
    return null;
  }
}

/**
 * Create a NEW device with a fresh ingest key. Returns the plaintext so the
 * browser can bake it into the firmware config during flashing.
 */
export async function provisionDeviceKey(
  name: string,
): Promise<{ id: string; plaintext: string; prefix: string }> {
  const tenantId = await requireTenant();
  const key = generateApiKey();
  const [row] = await db
    .insert(devices)
    .values({
      tenantId,
      name: name.trim().slice(0, 80) || "My Pala",
      keyPrefix: key.prefix,
      keyHash: key.hash,
    })
    .returning({ id: devices.id });
  return { id: row.id, plaintext: key.plaintext, prefix: key.prefix };
}

/** Record a successful flash on a device. Called by the flasher on success. */
export async function markDeviceFlashed(
  deviceRowId: string,
  firmwareVersion?: string,
): Promise<{ ok: true }> {
  const tenantId = await requireTenant();
  await db
    .update(devices)
    .set({ lastFlashedAt: new Date(), firmwareVersion: firmwareVersion?.slice(0, 32) ?? null })
    .where(and(eq(devices.id, deviceRowId), eq(devices.tenantId, tenantId)));
  return { ok: true };
}

/** List this workspace's active devices for the re-flash picker. */
export async function listDevices(): Promise<{ id: string; name: string; prefix: string }[]> {
  const tenantId = await requireTenant();
  const rows = await db
    .select({ id: devices.id, name: devices.name, prefix: devices.keyPrefix })
    .from(devices)
    .where(and(eq(devices.tenantId, tenantId), isNull(devices.revokedAt)))
    .orderBy(desc(devices.createdAt));
  return rows;
}

export interface DeviceView {
  id: string;
  name: string;
  prefix: string;
  deviceId: string | null;
  firmwareVersion: string | null;
  lastFlashedAt: string | null;
  lastSeenAt: string | null;
}

/** Full device details for the Devices page. */
export async function listDeviceDetails(): Promise<DeviceView[]> {
  const tenantId = await requireTenant();
  const rows = await db
    .select()
    .from(devices)
    .where(and(eq(devices.tenantId, tenantId), isNull(devices.revokedAt)))
    .orderBy(desc(devices.createdAt));
  return rows.map((d) => ({
    id: d.id,
    name: d.name,
    prefix: d.keyPrefix,
    deviceId: d.deviceId,
    firmwareVersion: d.firmwareVersion,
    lastFlashedAt: d.lastFlashedAt ? d.lastFlashedAt.toISOString() : null,
    lastSeenAt: d.lastSeenAt ? d.lastSeenAt.toISOString() : null,
  }));
}

/** Remove (delete) a device and its ingest key. */
export async function removeDevice(id: string): Promise<{ ok: true }> {
  const tenantId = await requireTenant();
  await db.delete(devices).where(and(eq(devices.id, id), eq(devices.tenantId, tenantId)));
  return { ok: true };
}

/**
 * Re-flashing an existing device: rotate its ingest key IN PLACE (regenerate
 * the hash on the same device row). The old token stops working immediately.
 */
export async function rotateDeviceKey(
  deviceRowId: string,
): Promise<{ id: string; plaintext: string; prefix: string } | { error: string }> {
  const tenantId = await requireTenant();
  const [existing] = await db
    .select({ id: devices.id })
    .from(devices)
    .where(and(eq(devices.id, deviceRowId), eq(devices.tenantId, tenantId)))
    .limit(1);
  if (!existing) return { error: "Device not found." };

  const key = generateApiKey();
  await db
    .update(devices)
    .set({ keyPrefix: key.prefix, keyHash: key.hash })
    .where(and(eq(devices.id, deviceRowId), eq(devices.tenantId, tenantId)));
  return { id: deviceRowId, plaintext: key.plaintext, prefix: key.prefix };
}

// --- saved Wi-Fi (up to two networks) --------------------------------------

function enc(v: string): string | null {
  return v.trim() ? encryptSecret(v.trim()) : null;
}
function dec(v: string | null): string {
  if (!v) return "";
  try {
    return decryptSecret(v);
  } catch {
    return "";
  }
}

/** Save (or update) the workspace's Wi-Fi networks for future flashes. */
export async function saveWifi(
  ssid: string,
  password: string,
  ssid2 = "",
  password2 = "",
): Promise<{ ok: true }> {
  const tenantId = await requireTenant();
  await db
    .update(tenants)
    .set({
      wifiSsidEnc: enc(ssid),
      wifiPassEnc: enc(password),
      wifiSsid2Enc: enc(ssid2),
      wifiPass2Enc: enc(password2),
    })
    .where(eq(tenants.id, tenantId));
  return { ok: true };
}

/** The (non-secret) saved SSIDs for prefill display, or null if none saved. */
export async function getSavedWifiInfo(): Promise<{ ssid: string; ssid2: string } | null> {
  const tenantId = await requireTenant();
  const [row] = await db
    .select({ ssidEnc: tenants.wifiSsidEnc, ssid2Enc: tenants.wifiSsid2Enc })
    .from(tenants)
    .where(eq(tenants.id, tenantId))
    .limit(1);
  if (!row?.ssidEnc) return null;
  return { ssid: dec(row.ssidEnc), ssid2: dec(row.ssid2Enc) };
}

/** Full saved Wi-Fi (incl. passwords) for baking into the device config. */
export async function getSavedWifi(): Promise<{
  ssid: string;
  password: string;
  ssid2: string;
  password2: string;
} | null> {
  const tenantId = await requireTenant();
  const [row] = await db
    .select({
      ssidEnc: tenants.wifiSsidEnc,
      passEnc: tenants.wifiPassEnc,
      ssid2Enc: tenants.wifiSsid2Enc,
      pass2Enc: tenants.wifiPass2Enc,
    })
    .from(tenants)
    .where(eq(tenants.id, tenantId))
    .limit(1);
  if (!row?.ssidEnc) return null;
  return {
    ssid: dec(row.ssidEnc),
    password: dec(row.passEnc),
    ssid2: dec(row.ssid2Enc),
    password2: dec(row.pass2Enc),
  };
}
