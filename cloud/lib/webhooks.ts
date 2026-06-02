import { createHmac, randomBytes, randomUUID } from "crypto";
import { and, asc, eq, lte } from "drizzle-orm";
import { db } from "@/db";
import { webhooks, webhookDeliveries } from "@/db/schema";

export const WEBHOOK_EVENTS = ["note.created"] as const;
export type WebhookEvent = (typeof WEBHOOK_EVENTS)[number];

// Backoff between attempts, in minutes. Attempt 1 is immediate (on enqueue);
// each later attempt is scheduled this long after the previous failure.
const BACKOFF_MIN = [1, 5, 30, 120, 360];
const MAX_ATTEMPTS = BACKOFF_MIN.length + 1; // 6 total

export function generateWebhookSecret(): string {
  return `whsec_${randomBytes(24).toString("base64url")}`;
}

export function signBody(secret: string, body: string): string {
  return createHmac("sha256", secret).update(body).digest("hex");
}

function eventBody(type: WebhookEvent, data: unknown): string {
  return JSON.stringify({
    id: `evt_${randomBytes(12).toString("base64url")}`,
    type,
    created_at: new Date().toISOString(),
    data,
  });
}

// One signed POST. Re-signs with a fresh timestamp on every attempt.
async function post(url: string, secret: string, deliveryId: string, type: string, body: string) {
  const timestamp = Math.floor(Date.now() / 1000).toString();
  const signature = signBody(secret, `${timestamp}.${body}`);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 5000);
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "User-Agent": "PalaNote-Webhooks/1",
        "X-Pala-Event": type,
        "X-Pala-Delivery": deliveryId,
        "X-Pala-Timestamp": timestamp,
        "X-Pala-Signature": `t=${timestamp},v1=${signature}`,
      },
      body,
      signal: controller.signal,
      cache: "no-store",
    });
    return { ok: res.ok, status: res.status, error: res.ok ? null : `HTTP ${res.status}` };
  } catch (e) {
    return { ok: false, status: 0, error: (e instanceof Error ? e.message : String(e)).slice(0, 200) };
  } finally {
    clearTimeout(timer);
  }
}

interface DeliveryRow {
  id: string;
  webhookId: string;
  eventType: string;
  body: string;
  attempts: number;
}

// Attempt one delivery and update its row (+ the webhook's summary status).
async function attempt(d: DeliveryRow, url: string, secret: string): Promise<void> {
  const attempts = d.attempts + 1;
  const r = await post(url, secret, d.id, d.eventType, d.body);
  const now = Date.now();

  if (r.ok) {
    await db
      .update(webhookDeliveries)
      .set({ status: "succeeded", attempts, lastStatus: r.status, lastError: null, deliveredAt: new Date() })
      .where(eq(webhookDeliveries.id, d.id));
  } else if (attempts >= MAX_ATTEMPTS) {
    await db
      .update(webhookDeliveries)
      .set({ status: "failed", attempts, lastStatus: r.status, lastError: r.error })
      .where(eq(webhookDeliveries.id, d.id));
  } else {
    const delayMs = BACKOFF_MIN[attempts - 1] * 60_000;
    await db
      .update(webhookDeliveries)
      .set({
        status: "pending",
        attempts,
        lastStatus: r.status,
        lastError: r.error,
        nextAttemptAt: new Date(now + delayMs),
      })
      .where(eq(webhookDeliveries.id, d.id));
  }

  // Mirror the latest attempt onto the webhook for the management UI.
  await db
    .update(webhooks)
    .set({ lastStatus: r.status, lastDeliveryAt: new Date(), lastError: r.error })
    .where(eq(webhooks.id, d.webhookId));
}

/**
 * Enqueue an event to all of a tenant's enabled, subscribed webhooks and make
 * the first delivery attempt immediately. Safe to fire-and-forget in `after()`.
 * Failed attempts are retried later by the cron worker (runDueDeliveries).
 */
export async function deliverEvent(
  tenantId: string,
  type: WebhookEvent,
  data: unknown,
): Promise<void> {
  try {
    const rows = await db
      .select({ id: webhooks.id, url: webhooks.url, secret: webhooks.secret, events: webhooks.events })
      .from(webhooks)
      .where(and(eq(webhooks.tenantId, tenantId), eq(webhooks.enabled, true)));
    const subscribed = rows.filter((r) => r.events.includes(type));
    if (!subscribed.length) return;

    const body = eventBody(type, data);
    for (const w of subscribed) {
      const [row] = await db
        .insert(webhookDeliveries)
        .values({ webhookId: w.id, tenantId, eventType: type, body, nextAttemptAt: new Date() })
        .returning({ id: webhookDeliveries.id });
      await attempt({ id: row.id, webhookId: w.id, eventType: type, body, attempts: 0 }, w.url, w.secret);
    }
  } catch (e) {
    console.error("[webhooks] enqueue error:", e instanceof Error ? e.message : e);
  }
}

/** Cron worker: attempt all pending deliveries whose backoff is due. */
export async function runDueDeliveries(limit = 50): Promise<{ processed: number }> {
  const due = await db
    .select({
      id: webhookDeliveries.id,
      webhookId: webhookDeliveries.webhookId,
      eventType: webhookDeliveries.eventType,
      body: webhookDeliveries.body,
      attempts: webhookDeliveries.attempts,
      url: webhooks.url,
      secret: webhooks.secret,
      enabled: webhooks.enabled,
    })
    .from(webhookDeliveries)
    .innerJoin(webhooks, eq(webhookDeliveries.webhookId, webhooks.id))
    .where(and(eq(webhookDeliveries.status, "pending"), lte(webhookDeliveries.nextAttemptAt, new Date())))
    .orderBy(asc(webhookDeliveries.nextAttemptAt))
    .limit(limit);

  let processed = 0;
  for (const d of due) {
    if (!d.enabled) {
      // Disabled webhook — stop retrying.
      await db.update(webhookDeliveries).set({ status: "failed", lastError: "webhook disabled" }).where(eq(webhookDeliveries.id, d.id));
      continue;
    }
    await attempt({ id: d.id, webhookId: d.webhookId, eventType: d.eventType, body: d.body, attempts: d.attempts }, d.url, d.secret);
    processed++;
  }
  return { processed };
}

/** Deliver a one-off sample event to a webhook (the "Send test" button). */
export async function deliverTest(
  tenantId: string,
  webhookId: string,
): Promise<{ ok: boolean; status: number; error?: string }> {
  const [w] = await db
    .select()
    .from(webhooks)
    .where(and(eq(webhooks.id, webhookId), eq(webhooks.tenantId, tenantId)))
    .limit(1);
  if (!w) return { ok: false, status: 0, error: "not found" };
  const body = eventBody("note.created", {
    note: {
      id: "00000000-0000-0000-0000-000000000000",
      note_num: 1,
      tag: "todo",
      text: "This is a test webhook delivery from Pala Note.",
      created_utc: new Date().toISOString(),
    },
  });
  const r = await post(w.url, w.secret, randomUUID(), "note.created", body);
  await db
    .update(webhooks)
    .set({ lastStatus: r.status, lastDeliveryAt: new Date(), lastError: r.error })
    .where(eq(webhooks.id, w.id));
  return { ok: r.ok, status: r.status, error: r.error ?? undefined };
}
