import { eq } from "drizzle-orm";
import { db } from "@/db";
import { todoistConfig, tenants, notes } from "@/db/schema";
import { decryptSecret } from "@/lib/crypto";
import { createTask } from "@/lib/todoist";
import { extractTasks } from "@/lib/task-ai";

/**
 * Turn a freshly-ingested, todo-tagged note into a Todoist task. Safe to call
 * fire-and-forget (e.g. inside `after()`): it never throws — failures are
 * logged and the note is simply left unlinked.
 */
export async function processNoteToTodoist(params: {
  tenantId: string;
  noteId: string;
  transcript: string;
}): Promise<void> {
  try {
    const [cfg] = await db
      .select()
      .from(todoistConfig)
      .where(eq(todoistConfig.tenantId, params.tenantId))
      .limit(1);
    if (!cfg) return; // Todoist not connected

    const [tenant] = await db
      .select({ openaiKeyEnc: tenants.openaiKeyEnc, instructions: tenants.instructions })
      .from(tenants)
      .where(eq(tenants.id, params.tenantId))
      .limit(1);
    if (!tenant?.openaiKeyEnc) {
      console.warn(`[todoist] tenant ${params.tenantId} has no OpenAI key; skipping task extraction`);
      return;
    }

    const openaiKey = decryptSecret(tenant.openaiKeyEnc);
    const token = decryptSecret(cfg.apiTokenEnc);

    console.log(
      `[todoist] processing note ${params.noteId}: ${cfg.projects.length} projects ` +
        `[${cfg.projects.map((p) => p.name).join(", ")}], ${cfg.labels.length} labels`,
    );

    const tasks = await extractTasks(openaiKey, {
      transcript: params.transcript,
      projects: cfg.projects,
      sections: cfg.sections,
      labels: cfg.labels,
      workspaceInstructions: tenant.instructions,
      customPrompt: cfg.customPrompt,
    });

    console.log(`[todoist] note ${params.noteId}: extracted ${tasks.length} task(s)`);

    const createdIds: string[] = [];
    for (const task of tasks) {
      try {
        const created = await createTask(token, {
          content: task.content,
          description: task.description ?? undefined,
          projectId: task.projectId,
          sectionId: task.sectionId,
          labels: task.labels,
          priority: task.priority,
          dueString: task.dueString ?? undefined,
        });
        createdIds.push(created.id);
        console.log(
          `[todoist]   -> task ${created.id} "${task.content}" in ${task.projectName ?? "Inbox"}` +
            (task.sectionName ? ` / ${task.sectionName}` : "") +
            (task.labels.length ? ` [${task.labels.join(", ")}]` : ""),
        );
      } catch (e) {
        console.error(`[todoist]   x failed to create "${task.content}":`, e instanceof Error ? e.message : e);
      }
    }

    if (createdIds.length) {
      await db
        .update(notes)
        .set({ todoistTaskId: createdIds.join(",") })
        .where(eq(notes.id, params.noteId));
    }
  } catch (e) {
    console.error("[todoist] failed to create task:", e instanceof Error ? e.message : e);
  }
}

/** Whether a tenant has Todoist connected and which tag triggers a task. */
export async function getTodoTrigger(tenantId: string): Promise<{ todoTag: string } | null> {
  const [cfg] = await db
    .select({ todoTag: todoistConfig.todoTag })
    .from(todoistConfig)
    .where(eq(todoistConfig.tenantId, tenantId))
    .limit(1);
  return cfg ?? null;
}
