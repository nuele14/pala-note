// Turn a voice-note transcript into one or more structured Todoist tasks using
// the user's own OpenAI key (kept cheap + off our quota). Uses Chat Completions
// with a strict JSON schema so the output is reliable.

import type { TodoistProject, TodoistSection } from "@/lib/todoist";

const MODEL = "gpt-4o-mini";
const MAX_TASKS = 25;

export interface ExtractedTask {
  content: string;
  description: string | null;
  projectId: string | null;
  projectName: string | null; // resolved project, for logging/diagnostics
  sectionId: string | null;
  sectionName: string | null;
  labels: string[];
  priority: number; // 1..4
  dueString: string | null;
}

interface ExtractParams {
  transcript: string;
  projects: TodoistProject[];
  sections: TodoistSection[];
  labels: string[];
  workspaceInstructions: string;
  customPrompt: string;
}

function systemPrompt(params: ExtractParams): string {
  // Models reliably echo a NAME but often mangle opaque ids — so we ask for the
  // project/section name and map it back to an id ourselves. Sections are listed
  // beneath their project so the model only picks a valid sub-section.
  const projectLines = params.projects.length
    ? params.projects
        .map((p) => {
          const secs = params.sections.filter((s) => s.projectId === p.id).map((s) => s.name);
          return secs.length ? `- ${p.name}\n    sub-sections: ${secs.join(", ")}` : `- ${p.name}`;
        })
        .join("\n")
    : "(none)";
  const labelLines = params.labels.length ? params.labels.map((l) => `- ${l}`).join("\n") : "(none)";

  return [
    "You convert a short, possibly messy voice-note transcript into Todoist tasks.",
    "A single note may contain SEVERAL distinct action items — return ONE task object per distinct action in the `tasks` array.",
    "Guidance on splitting:",
    "- Create a separate task for each independent action (e.g. \"email Sam, book a flight, and buy milk\" → 3 tasks).",
    "- Do NOT merge unrelated actions into one task; do NOT split a single action into many.",
    "- Each task can be routed to a different project/section as appropriate.",
    "Per task:",
    '- `content`: a concise, imperative title (e.g. "Call the dentist").',
    "- `description`: extra detail for that item, or null.",
    "- `project_name`: EXACTLY one of the allowed project names below, or null if none clearly fits. Never invent a project.",
    "- `section_name`: EXACTLY one of the sub-sections listed under the project you chose, or null. Never use a sub-section from a different project.",
    "- `labels`: a subset of the allowed labels below (exact strings), or [].",
    "- `priority`: 1 (normal) to 4 (urgent); default 1.",
    '- `due_string`: a natural-language due date ONLY if implied (e.g. "tomorrow"), else null.',
    "",
    "Allowed projects (use the name exactly; pick a sub-section only from the chosen project):",
    projectLines,
    "",
    "Allowed labels:",
    labelLines,
    params.workspaceInstructions.trim()
      ? `\nWorkspace context (about the user):\n${params.workspaceInstructions.trim()}`
      : "",
    params.customPrompt.trim()
      ? `\nUser's task-routing instructions (follow these closely when choosing the project, section, and labels):\n${params.customPrompt.trim()}`
      : "",
  ].join("\n");
}

const TASK_PROPS = {
  type: "object",
  additionalProperties: false,
  required: ["content", "description", "project_name", "section_name", "labels", "priority", "due_string"],
  properties: {
    content: { type: "string" },
    description: { type: ["string", "null"] },
    project_name: { type: ["string", "null"] },
    section_name: { type: ["string", "null"] },
    labels: { type: "array", items: { type: "string" } },
    priority: { type: "integer", enum: [1, 2, 3, 4] },
    due_string: { type: ["string", "null"] },
  },
} as const;

const SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["tasks"],
  properties: {
    tasks: { type: "array", items: TASK_PROPS },
  },
} as const;

interface RawTask {
  content: string;
  description: string | null;
  project_name: string | null;
  section_name: string | null;
  labels: string[];
  priority: number;
  due_string: string | null;
}

function resolveTask(raw: RawTask, params: ExtractParams, transcript: string): ExtractedTask {
  // Project: match by name (case-insensitive); tolerate the model returning an id.
  let project: TodoistProject | undefined;
  if (raw.project_name) {
    const wanted = raw.project_name.trim().toLowerCase();
    project =
      params.projects.find((p) => p.name.toLowerCase() === wanted) ??
      params.projects.find((p) => p.id === raw.project_name) ??
      params.projects.find((p) => p.name.toLowerCase().includes(wanted) && wanted.length > 2);
  }

  // Section: scoped to the chosen project.
  let section: TodoistSection | undefined;
  if (project && raw.section_name) {
    const wanted = raw.section_name.trim().toLowerCase();
    section = params.sections.find(
      (s) => s.projectId === project!.id && s.name.toLowerCase() === wanted,
    );
  }

  const labelByLower = new Map(params.labels.map((l) => [l.toLowerCase(), l]));
  const labels = (raw.labels ?? [])
    .map((l) => labelByLower.get(l.trim().toLowerCase()))
    .filter((l): l is string => !!l);

  return {
    content: raw.content?.slice(0, 500) || transcript.slice(0, 120),
    description: raw.description || null,
    projectId: project?.id ?? null,
    projectName: project?.name ?? null,
    sectionId: section?.id ?? null,
    sectionName: section?.name ?? null,
    labels,
    priority: raw.priority >= 1 && raw.priority <= 4 ? raw.priority : 1,
    dueString: raw.due_string || null,
  };
}

/** Extract one or more tasks from a transcript. */
export async function extractTasks(
  openaiKey: string,
  params: ExtractParams,
): Promise<ExtractedTask[]> {
  const res = await fetch("https://api.openai.com/v1/chat/completions", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${openaiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: MODEL,
      temperature: 0.2,
      messages: [
        { role: "system", content: systemPrompt(params) },
        { role: "user", content: params.transcript },
      ],
      response_format: {
        type: "json_schema",
        json_schema: { name: "todoist_tasks", strict: true, schema: SCHEMA },
      },
    }),
  });
  if (!res.ok) {
    throw new Error(`OpenAI task extraction -> ${res.status}: ${await res.text().catch(() => "")}`);
  }
  const data = await res.json();
  const parsed = JSON.parse(data.choices[0].message.content) as { tasks: RawTask[] };
  const rawTasks = (parsed.tasks ?? []).slice(0, MAX_TASKS);

  // Fall back to a single task built from the transcript if the model returned none.
  if (rawTasks.length === 0) {
    return [
      resolveTask(
        {
          content: params.transcript.slice(0, 120),
          description: null,
          project_name: null,
          section_name: null,
          labels: [],
          priority: 1,
          due_string: null,
        },
        params,
        params.transcript,
      ),
    ];
  }

  return rawTasks.map((t) => resolveTask(t, params, params.transcript));
}
