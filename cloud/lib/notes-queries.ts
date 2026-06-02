import { and, desc, eq, ilike, isNull, isNotNull, lt, sql } from "drizzle-orm";
import { db } from "@/db";
import { notes, tenants } from "@/db/schema";

/** The workspace's persistent AI-context instructions (empty string if unset). */
export async function getInstructions(tenantId: string): Promise<string> {
  const [row] = await db
    .select({ instructions: tenants.instructions })
    .from(tenants)
    .where(eq(tenants.id, tenantId))
    .limit(1);
  return row?.instructions ?? "";
}

export interface NoteSummary {
  id: string;
  note_num: number;
  tag: string | null;
  created_utc: string | null;
  text_preview: string;
}

function preview(text: string, len = 160): string {
  const t = text.replace(/\s+/g, " ").trim();
  return t.length > len ? t.slice(0, len) + "…" : t;
}

/** List a tenant's notes, newest first, with optional tag/since filters and cursor paging. */
export async function listNotes(
  tenantId: string,
  opts: { tag?: string; since?: string; limit?: number; cursor?: string; archived?: boolean } = {},
): Promise<{ notes: NoteSummary[]; next_cursor: string | null }> {
  const limit = Math.min(Math.max(opts.limit ?? 25, 1), 100);
  const conds = [eq(notes.tenantId, tenantId)];
  // Archived notes are hidden from the default list (and from the MCP).
  conds.push(opts.archived ? isNotNull(notes.archivedAt) : isNull(notes.archivedAt));
  if (opts.tag) conds.push(eq(notes.tag, opts.tag));
  if (opts.since) conds.push(sql`${notes.createdUtc} >= ${new Date(opts.since)}`);
  // Cursor is the uploadedAt ISO timestamp of the last row seen.
  if (opts.cursor) conds.push(lt(notes.uploadedAt, new Date(opts.cursor)));

  const rows = await db
    .select()
    .from(notes)
    .where(and(...conds))
    .orderBy(desc(notes.uploadedAt))
    .limit(limit + 1);

  const page = rows.slice(0, limit);
  const next = rows.length > limit ? page[page.length - 1].uploadedAt.toISOString() : null;

  return {
    notes: page.map((n) => ({
      id: n.id,
      note_num: n.deviceNoteNum,
      tag: n.tag,
      created_utc: n.createdUtc?.toISOString() ?? null,
      text_preview: preview(n.text),
    })),
    next_cursor: next,
  };
}

export async function getNote(tenantId: string, id: string) {
  const [n] = await db
    .select()
    .from(notes)
    .where(and(eq(notes.tenantId, tenantId), eq(notes.id, id)))
    .limit(1);
  if (!n) return null;
  return {
    id: n.id,
    note_num: n.deviceNoteNum,
    tag: n.tag,
    created_utc: n.createdUtc?.toISOString() ?? null,
    uploaded_at: n.uploadedAt.toISOString(),
    text: n.text,
    archived: !!n.archivedAt,
  };
}

export async function searchNotes(tenantId: string, query: string, limit = 25) {
  const rows = await db
    .select()
    .from(notes)
    .where(
      and(
        eq(notes.tenantId, tenantId),
        isNull(notes.archivedAt),
        ilike(notes.text, `%${query}%`),
      ),
    )
    .orderBy(desc(notes.createdUtc))
    .limit(Math.min(Math.max(limit, 1), 100));
  return rows.map((n) => ({
    id: n.id,
    note_num: n.deviceNoteNum,
    tag: n.tag,
    created_utc: n.createdUtc?.toISOString() ?? null,
    text_preview: preview(n.text),
  }));
}

export async function listTags(tenantId: string) {
  const rows = await db
    .select({ tag: notes.tag, count: sql<number>`count(*)::int` })
    .from(notes)
    .where(and(eq(notes.tenantId, tenantId), isNull(notes.archivedAt)))
    .groupBy(notes.tag)
    .orderBy(desc(sql`count(*)`));
  return rows.filter((r) => r.tag).map((r) => ({ tag: r.tag as string, count: r.count }));
}

/** Count of archived notes (for the Archived filter pill). */
export async function countArchived(tenantId: string): Promise<number> {
  const [row] = await db
    .select({ count: sql<number>`count(*)::int` })
    .from(notes)
    .where(and(eq(notes.tenantId, tenantId), isNotNull(notes.archivedAt)));
  return row?.count ?? 0;
}
