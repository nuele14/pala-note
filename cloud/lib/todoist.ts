// Thin Todoist client on the unified API v1 (the REST v2 API was shut down in
// Feb 2026). We use the HTTP API (not an MCP) to keep token usage minimal.
// Docs: https://developer.todoist.com/api/v1

const BASE = "https://api.todoist.com/api/v1";

export interface TodoistProject {
  id: string;
  name: string;
}

export interface TodoistSection {
  id: string;
  name: string;
  projectId: string;
}

/**
 * Fetch a collection, tolerating both shapes the v1 API may return: a plain
 * array, or a paginated `{ results, next_cursor }` wrapper.
 */
async function fetchAll<T>(token: string, path: string): Promise<T[]> {
  const out: T[] = [];
  let url = `${BASE}${path}`;
  for (let i = 0; i < 20; i++) {
    const res = await fetch(url, {
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    if (!res.ok) throw new Error(`Todoist ${path} -> ${res.status}`);
    const data = await res.json();
    if (Array.isArray(data)) {
      out.push(...(data as T[]));
      break;
    }
    out.push(...((data.results ?? []) as T[]));
    if (!data.next_cursor) break;
    const sep = path.includes("?") ? "&" : "?";
    url = `${BASE}${path}${sep}cursor=${encodeURIComponent(data.next_cursor)}`;
  }
  return out;
}

/** Validate a token by listing projects. Returns true if the token works. */
export async function verifyTodoistToken(token: string): Promise<boolean> {
  try {
    const res = await fetch(`${BASE}/projects`, {
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    return res.ok;
  } catch {
    return false;
  }
}

export async function fetchProjects(token: string): Promise<TodoistProject[]> {
  const rows = await fetchAll<{ id: string | number; name: string }>(token, "/projects");
  return rows.map((p) => ({ id: String(p.id), name: p.name }));
}

export async function fetchLabels(token: string): Promise<string[]> {
  const rows = await fetchAll<{ name: string }>(token, "/labels");
  return rows.map((l) => l.name).filter(Boolean);
}

export async function fetchSections(token: string): Promise<TodoistSection[]> {
  const rows = await fetchAll<{ id: string | number; project_id: string | number; name: string }>(
    token,
    "/sections",
  );
  return rows.map((s) => ({ id: String(s.id), name: s.name, projectId: String(s.project_id) }));
}

export interface CreateTaskInput {
  content: string;
  description?: string;
  projectId?: string | null;
  sectionId?: string | null;
  labels?: string[];
  priority?: number; // 1..4 (4 = highest)
  dueString?: string;
}

export async function createTask(
  token: string,
  input: CreateTaskInput,
): Promise<{ id: string; url: string }> {
  const body: Record<string, unknown> = { content: input.content };
  if (input.description) body.description = input.description;
  if (input.projectId) body.project_id = input.projectId;
  if (input.sectionId) body.section_id = input.sectionId;
  if (input.labels?.length) body.labels = input.labels;
  if (input.priority && input.priority >= 1 && input.priority <= 4) body.priority = input.priority;
  if (input.dueString) body.due_string = input.dueString;

  const res = await fetch(`${BASE}/tasks`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw new Error(`Todoist createTask -> ${res.status}: ${await res.text().catch(() => "")}`);
  }
  const task = (await res.json()) as { id: string | number; url?: string };
  return { id: String(task.id), url: task.url ?? `https://todoist.com/showTask?id=${task.id}` };
}
