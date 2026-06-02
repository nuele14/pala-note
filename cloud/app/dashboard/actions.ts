"use server";

import { and, eq } from "drizzle-orm";
import { revalidatePath } from "next/cache";
import { auth, signOut } from "@/auth";
import { db } from "@/db";
import { tenants, notes } from "@/db/schema";

async function requireTenant(): Promise<string> {
  const session = await auth();
  const tenantId = session?.user?.tenantId;
  if (!tenantId) throw new Error("Not authenticated");
  return tenantId;
}

export async function signOutAction() {
  await signOut({ redirectTo: "/login" });
}

export async function saveInstructions(instructions: string): Promise<{ ok: true }> {
  const tenantId = await requireTenant();
  await db
    .update(tenants)
    .set({ instructions: instructions.slice(0, 10_000) })
    .where(eq(tenants.id, tenantId));
  revalidatePath("/dashboard/settings");
  return { ok: true };
}

export async function setNoteArchived(
  noteId: string,
  archived: boolean,
): Promise<{ ok: true }> {
  const tenantId = await requireTenant();
  await db
    .update(notes)
    .set({ archivedAt: archived ? new Date() : null })
    .where(and(eq(notes.id, noteId), eq(notes.tenantId, tenantId)));
  revalidatePath("/dashboard/notes");
  revalidatePath(`/dashboard/notes/${noteId}`);
  return { ok: true };
}

export async function saveTimezone(timezone: string): Promise<{ ok: true }> {
  const tenantId = await requireTenant();
  // Validate it's a real IANA zone before storing.
  let tz = "America/Los_Angeles";
  try {
    Intl.DateTimeFormat("en-US", { timeZone: timezone });
    tz = timezone;
  } catch {
    /* keep default */
  }
  await db.update(tenants).set({ timezone: tz }).where(eq(tenants.id, tenantId));
  revalidatePath("/dashboard", "layout");
  return { ok: true };
}

export async function renameWorkspace(name: string): Promise<{ ok: true }> {
  const tenantId = await requireTenant();
  const clean = name.trim().slice(0, 120) || "My workspace";
  await db.update(tenants).set({ name: clean }).where(eq(tenants.id, tenantId));
  revalidatePath("/dashboard/settings");
  revalidatePath("/dashboard", "layout");
  return { ok: true };
}
