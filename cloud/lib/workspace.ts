import { eq, sql, and, isNull } from "drizzle-orm";
import { db } from "@/db";
import { tenants, notes, devices } from "@/db/schema";

export interface Workspace {
  id: string;
  name: string;
  instructions: string;
  timezone: string;
}

export async function getWorkspace(tenantId: string): Promise<Workspace | null> {
  const [t] = await db
    .select({
      id: tenants.id,
      name: tenants.name,
      instructions: tenants.instructions,
      timezone: tenants.timezone,
    })
    .from(tenants)
    .where(eq(tenants.id, tenantId))
    .limit(1);
  return t ?? null;
}

/** Lightweight counts for the workspace. */
export async function getWorkspaceStats(tenantId: string) {
  const [[noteRow], [devRow]] = await Promise.all([
    db
      .select({ count: sql<number>`count(*)::int` })
      .from(notes)
      .where(eq(notes.tenantId, tenantId)),
    db
      .select({ count: sql<number>`count(*)::int` })
      .from(devices)
      .where(and(eq(devices.tenantId, tenantId), isNull(devices.revokedAt))),
  ]);
  return { notes: noteRow?.count ?? 0, devices: devRow?.count ?? 0 };
}
