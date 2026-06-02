"use server";

import { and, desc, eq, sql } from "drizzle-orm";
import { revalidatePath } from "next/cache";
import { auth } from "@/auth";
import { db } from "@/db";
import { webhooks, webhookDeliveries } from "@/db/schema";
import { generateWebhookSecret, deliverTest } from "@/lib/webhooks";

async function requireTenant(): Promise<string> {
  const session = await auth();
  const tenantId = session?.user?.tenantId;
  if (!tenantId) throw new Error("Not authenticated");
  return tenantId;
}

export interface WebhookView {
  id: string;
  url: string;
  secret: string;
  events: string[];
  enabled: boolean;
  lastStatus: number | null;
  lastDeliveryAt: string | null;
  lastError: string | null;
  pending: number; // deliveries queued for retry
}

export async function listWebhooks(): Promise<WebhookView[]> {
  const tenantId = await requireTenant();
  const rows = await db
    .select()
    .from(webhooks)
    .where(eq(webhooks.tenantId, tenantId))
    .orderBy(desc(webhooks.createdAt));

  const pendingRows = await db
    .select({ webhookId: webhookDeliveries.webhookId, count: sql<number>`count(*)::int` })
    .from(webhookDeliveries)
    .where(and(eq(webhookDeliveries.tenantId, tenantId), eq(webhookDeliveries.status, "pending")))
    .groupBy(webhookDeliveries.webhookId);
  const pendingBy = new Map(pendingRows.map((r) => [r.webhookId, r.count]));

  return rows.map((w) => ({
    id: w.id,
    url: w.url,
    secret: w.secret,
    events: w.events,
    enabled: w.enabled,
    lastStatus: w.lastStatus,
    lastDeliveryAt: w.lastDeliveryAt ? w.lastDeliveryAt.toISOString() : null,
    lastError: w.lastError,
    pending: pendingBy.get(w.id) ?? 0,
  }));
}

export async function createWebhook(url: string): Promise<{ ok: boolean; error?: string }> {
  const tenantId = await requireTenant();
  const clean = url.trim();
  let parsed: URL;
  try {
    parsed = new URL(clean);
  } catch {
    return { ok: false, error: "Enter a valid URL." };
  }
  if (parsed.protocol !== "https:") {
    return { ok: false, error: "Webhook URL must be https." };
  }
  await db.insert(webhooks).values({
    tenantId,
    url: clean,
    secret: generateWebhookSecret(),
    events: ["note.created"],
  });
  revalidatePath("/dashboard/developers");
  return { ok: true };
}

export async function deleteWebhook(id: string): Promise<{ ok: true }> {
  const tenantId = await requireTenant();
  await db.delete(webhooks).where(and(eq(webhooks.id, id), eq(webhooks.tenantId, tenantId)));
  revalidatePath("/dashboard/developers");
  return { ok: true };
}

export async function toggleWebhook(id: string, enabled: boolean): Promise<{ ok: true }> {
  const tenantId = await requireTenant();
  await db
    .update(webhooks)
    .set({ enabled })
    .where(and(eq(webhooks.id, id), eq(webhooks.tenantId, tenantId)));
  revalidatePath("/dashboard/developers");
  return { ok: true };
}

export async function sendTestWebhook(
  id: string,
): Promise<{ ok: boolean; status: number; error?: string }> {
  const tenantId = await requireTenant();
  const res = await deliverTest(tenantId, id);
  revalidatePath("/dashboard/developers");
  return res;
}
