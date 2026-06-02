/**
 * Diagnostic: run the task extractor against the tenant's REAL stored Todoist
 * projects/labels + custom prompt, using their OpenAI key. Prints which project
 * each sample transcript routes to. Does NOT create Todoist tasks.
 *
 * Run: set -a && . ./.env.local && set +a && npx tsx scripts/diag-todoist.ts ["custom transcript"]
 */
import { eq } from "drizzle-orm";
import { db, schema } from "../db";
import { decryptSecret } from "../lib/crypto";
import { extractTasks } from "../lib/task-ai";
import { fetchSections } from "../lib/todoist";

const SAMPLES = [
  "Remind me to email the design team about the new logo by Friday",
  "Buy oat milk and coffee filters",
  "Schedule a dentist appointment for Penny and Theo",
  "Follow up with the client about the contract next week",
];

async function main() {
  const custom = process.argv.slice(2).join(" ").trim();
  const transcripts = custom ? [custom] : SAMPLES;

  const cfgs = await db.select().from(schema.todoistConfig);
  if (!cfgs.length) {
    console.log("No todoist_config rows — Todoist isn't connected for any tenant.");
    process.exit(0);
  }

  for (const cfg of cfgs) {
    const [t] = await db
      .select()
      .from(schema.tenants)
      .where(eq(schema.tenants.id, cfg.tenantId));

    // Sections may be empty if the tenant connected before sections existed —
    // fetch live from the stored token so the diagnostic reflects reality.
    let sections = cfg.sections;
    if (!sections.length) {
      try {
        sections = await fetchSections(decryptSecret(cfg.apiTokenEnc));
        console.log(`(fetched ${sections.length} sections live from token)`);
      } catch {
        /* ignore */
      }
    }

    console.log("\n=== tenant", cfg.tenantId, "===");
    console.log("projects:", cfg.projects.map((p) => `${p.name}`).join(", ") || "(none)");
    console.log("sections:", sections.map((s) => s.name).join(", ") || "(none)");
    console.log("labels:", cfg.labels.join(", ") || "(none)");
    console.log("todoTag:", cfg.todoTag);
    console.log("customPrompt:", JSON.stringify(cfg.customPrompt));
    console.log("workspaceInstructions:", JSON.stringify(t?.instructions ?? ""));

    if (!t?.openaiKeyEnc) {
      console.log("-> no OpenAI key stored; cannot extract.");
      continue;
    }
    const openai = decryptSecret(t.openaiKeyEnc);

    for (const tr of transcripts) {
      try {
        const tasks = await extractTasks(openai, {
          transcript: tr,
          projects: cfg.projects,
          sections,
          labels: cfg.labels,
          workspaceInstructions: t.instructions,
          customPrompt: cfg.customPrompt,
        });
        console.log(`\n  "${tr}"  -> ${tasks.length} task(s)`);
        for (const task of tasks) {
          console.log(
            `     - "${task.content}" | project=${task.projectName ?? "(Inbox)"} | section=${task.sectionName ?? "-"} | labels=[${task.labels.join(", ")}] | p${task.priority} | due=${task.dueString ?? "-"}`,
          );
        }
      } catch (e) {
        console.log(`  "${tr}" -> ERROR:`, e instanceof Error ? e.message : e);
      }
    }
  }
  process.exit(0);
}

main();
