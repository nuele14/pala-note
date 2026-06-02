import { NextResponse } from "next/server";
import { after } from "next/server";
import { z } from "zod";
import { eq } from "drizzle-orm";
import { db } from "@/db";
import { notes, devices } from "@/db/schema";
import { parseBearer, resolveDeviceKey } from "@/lib/bearer-auth";
import { noteDedupeKey } from "@/lib/api-keys";
import { getTodoTrigger, processNoteToTodoist } from "@/lib/todoist-processing";
import { rateLimit, clientIp, tooManyRequests } from "@/lib/rate-limit";
import { deliverEvent, runDueDeliveries } from "@/lib/webhooks";

export const runtime = "nodejs";

const BodySchema = z.object({
  device_id: z.string().min(1).max(128),
  note_num: z.number().int().nonnegative(),
  tag: z.string().max(64).nullish(),
  created_utc: z.string().datetime().nullish(),
  text: z.string().max(100_000),
});

export async function POST(req: Request) {
  if (!rateLimit(`ingest:${clientIp(req)}`, 120, 60_000)) {
    return NextResponse.json({ error: "rate_limited" }, { status: 429 });
  }

  const device = await resolveDeviceKey(parseBearer(req.headers.get("authorization")));
  if (!device) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }

  let parsed;
  try {
    parsed = BodySchema.parse(await req.json());
  } catch {
    return NextResponse.json({ error: "bad_request" }, { status: 400 });
  }

  const createdUtc = parsed.created_utc ?? null;
  const dedupeKey = noteDedupeKey(parsed.device_id, parsed.note_num, createdUtc);

  // Record that this device just synced, and learn its self-reported id.
  void db
    .update(devices)
    .set({ lastSeenAt: new Date(), deviceId: parsed.device_id })
    .where(eq(devices.id, device.deviceId))
    .catch(() => {});

  const inserted = await db
    .insert(notes)
    .values({
      tenantId: device.tenantId,
      deviceNoteNum: parsed.note_num,
      text: parsed.text,
      tag: parsed.tag ?? null,
      createdUtc: createdUtc ? new Date(createdUtc) : null,
      dedupeKey,
    })
    .onConflictDoNothing({ target: [notes.tenantId, notes.dedupeKey] })
    .returning({ id: notes.id });

  if (inserted.length === 0) {
    return NextResponse.json({ status: "duplicate" });
  }

  const noteId = inserted[0].id;

  // Fire the note.created webhook after responding (keeps the device's upload
  // fast), and opportunistically sweep any due retries — so failed deliveries
  // advance on normal traffic without needing a per-minute cron.
  after(async () => {
    await deliverEvent(device.tenantId, "note.created", {
      note: {
        id: noteId,
        note_num: parsed.note_num,
        tag: parsed.tag ?? null,
        text: parsed.text,
        created_utc: createdUtc,
        device_id: parsed.device_id,
      },
    });
    await runDueDeliveries(20).catch(() => {});
  });

  // If this note is tagged as a task and Todoist is connected, turn it into a
  // task after the response too.
  if (parsed.tag) {
    const trigger = await getTodoTrigger(device.tenantId);
    if (trigger && parsed.tag.toLowerCase() === trigger.todoTag.toLowerCase()) {
      after(() =>
        processNoteToTodoist({ tenantId: device.tenantId, noteId, transcript: parsed.text }),
      );
    }
  }

  return NextResponse.json({ status: "created", id: noteId });
}
