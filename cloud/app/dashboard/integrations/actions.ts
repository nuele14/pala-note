"use server";

import { eq } from "drizzle-orm";
import { revalidatePath } from "next/cache";
import { auth } from "@/auth";
import { db } from "@/db";
import { todoistConfig } from "@/db/schema";
import { encryptSecret, decryptSecret } from "@/lib/crypto";
import { verifyTodoistToken, fetchProjects, fetchLabels, fetchSections } from "@/lib/todoist";

async function requireTenant(): Promise<string> {
  const session = await auth();
  const tenantId = session?.user?.tenantId;
  if (!tenantId) throw new Error("Not authenticated");
  return tenantId;
}

export interface TodoistView {
  connected: boolean;
  todoTag: string;
  customPrompt: string;
  projectCount: number;
  sectionCount: number;
  labelCount: number;
  syncedAt: string | null;
}

export async function getTodoistView(): Promise<TodoistView> {
  const tenantId = await requireTenant();
  const [cfg] = await db
    .select()
    .from(todoistConfig)
    .where(eq(todoistConfig.tenantId, tenantId))
    .limit(1);
  if (!cfg) {
    return {
      connected: false,
      todoTag: "todo",
      customPrompt: "",
      projectCount: 0,
      sectionCount: 0,
      labelCount: 0,
      syncedAt: null,
    };
  }
  return {
    connected: true,
    todoTag: cfg.todoTag,
    customPrompt: cfg.customPrompt,
    projectCount: cfg.projects.length,
    sectionCount: cfg.sections.length,
    labelCount: cfg.labels.length,
    syncedAt: cfg.syncedAt ? cfg.syncedAt.toISOString() : null,
  };
}

export async function connectTodoist(
  token: string,
): Promise<{ ok: boolean; error?: string }> {
  const tenantId = await requireTenant();
  const clean = token.trim();
  if (!clean) return { ok: false, error: "Paste your Todoist API token." };

  if (!(await verifyTodoistToken(clean))) {
    return { ok: false, error: "Todoist rejected that token. Copy it from Settings → Integrations → Developer." };
  }

  const [projects, labels, sections] = await Promise.all([
    fetchProjects(clean),
    fetchLabels(clean),
    fetchSections(clean),
  ]);

  await db
    .insert(todoistConfig)
    .values({
      tenantId,
      apiTokenEnc: encryptSecret(clean),
      projects,
      sections,
      labels,
      syncedAt: new Date(),
    })
    .onConflictDoUpdate({
      target: todoistConfig.tenantId,
      set: { apiTokenEnc: encryptSecret(clean), projects, sections, labels, syncedAt: new Date() },
    });

  revalidatePath("/dashboard/integrations");
  return { ok: true };
}

export async function syncTodoistMetadata(): Promise<{ ok: boolean; error?: string }> {
  const tenantId = await requireTenant();
  const [cfg] = await db
    .select()
    .from(todoistConfig)
    .where(eq(todoistConfig.tenantId, tenantId))
    .limit(1);
  if (!cfg) return { ok: false, error: "Connect Todoist first." };

  try {
    const token = decryptSecret(cfg.apiTokenEnc);
    const [projects, labels, sections] = await Promise.all([
      fetchProjects(token),
      fetchLabels(token),
      fetchSections(token),
    ]);
    await db
      .update(todoistConfig)
      .set({ projects, sections, labels, syncedAt: new Date() })
      .where(eq(todoistConfig.tenantId, tenantId));
    revalidatePath("/dashboard/integrations");
    return { ok: true };
  } catch {
    return { ok: false, error: "Couldn't reach Todoist. Check your token and try again." };
  }
}

export async function saveTodoistSettings(
  customPrompt: string,
  todoTag: string,
): Promise<{ ok: true }> {
  const tenantId = await requireTenant();
  await db
    .update(todoistConfig)
    .set({
      customPrompt: customPrompt.slice(0, 4000),
      todoTag: todoTag.trim().slice(0, 64) || "todo",
    })
    .where(eq(todoistConfig.tenantId, tenantId));
  revalidatePath("/dashboard/integrations");
  return { ok: true };
}

export async function disconnectTodoist(): Promise<{ ok: true }> {
  const tenantId = await requireTenant();
  await db.delete(todoistConfig).where(eq(todoistConfig.tenantId, tenantId));
  revalidatePath("/dashboard/integrations");
  return { ok: true };
}
