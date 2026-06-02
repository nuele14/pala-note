import { runDueDeliveries } from "@/lib/webhooks";

export const runtime = "nodejs";
export const maxDuration = 60;

// Invoked by Vercel Cron (see vercel.json). Vercel attaches
// `Authorization: Bearer $CRON_SECRET` when CRON_SECRET is set.
export async function GET(req: Request) {
  const secret = process.env.CRON_SECRET;
  if (!secret || req.headers.get("authorization") !== `Bearer ${secret}`) {
    return new Response("unauthorized", { status: 401 });
  }
  const res = await runDueDeliveries();
  return Response.json({ ok: true, ...res });
}
