// Best-effort, in-memory fixed-window rate limiter. NOTE: on serverless this is
// per-instance (not shared across Vercel lambdas), so it blunts casual abuse but
// is NOT a hard guarantee — move to a shared store (e.g. Upstash) for that.

interface Bucket {
  count: number;
  reset: number;
}
const buckets = new Map<string, Bucket>();

/** Returns true if allowed, false if the limit is exceeded for this window. */
export function rateLimit(key: string, limit: number, windowMs: number): boolean {
  const now = Date.now();
  const b = buckets.get(key);
  if (!b || now > b.reset) {
    buckets.set(key, { count: 1, reset: now + windowMs });
    // Opportunistic prune so the map can't grow unbounded on a warm instance.
    if (buckets.size > 5000) {
      for (const [k, v] of buckets) if (now > v.reset) buckets.delete(k);
    }
    return true;
  }
  if (b.count >= limit) return false;
  b.count++;
  return true;
}

export function clientIp(req: Request): string {
  const xff = req.headers.get("x-forwarded-for");
  if (xff) return xff.split(",")[0].trim();
  return req.headers.get("x-real-ip") ?? "unknown";
}

export function tooManyRequests(): Response {
  return Response.json(
    { error: "rate_limited", error_description: "Too many requests. Slow down." },
    { status: 429, headers: { "Retry-After": "60" } },
  );
}
