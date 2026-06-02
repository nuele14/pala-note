import Link from "next/link";
import { FileText, Mic, Archive } from "lucide-react";
import { requireSession } from "@/lib/session";
import {
  listNotes,
  searchNotes,
  listTags,
  countArchived,
  type NoteSummary,
} from "@/lib/notes-queries";
import { PageHeader } from "@/components/dashboard/page-header";
import { NotesSearch } from "@/components/dashboard/notes-search";
import { NoteArchiveButton } from "@/components/dashboard/note-archive-button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { getWorkspace } from "@/lib/workspace";
import { formatNoteTime } from "@/lib/format";

export default async function NotesPage({
  searchParams,
}: {
  searchParams: Promise<{ tag?: string; q?: string; view?: string }>;
}) {
  const { tenantId } = await requireSession();
  const { tag, q, view } = await searchParams;
  const archivedView = view === "archived";

  const [tags, archivedCount, notes, workspace] = await Promise.all([
    listTags(tenantId),
    countArchived(tenantId),
    archivedView
      ? listNotes(tenantId, { archived: true, limit: 100 }).then((r) => r.notes)
      : q
        ? searchNotes(tenantId, q, 100)
        : listNotes(tenantId, { tag, limit: 100 }).then((r) => r.notes),
    getWorkspace(tenantId),
  ]);
  const tz = workspace?.timezone ?? "America/Los_Angeles";

  const totalNotes = tags.reduce((n, t) => n + t.count, 0);

  return (
    <div className="space-y-6">
      <PageHeader
        title={archivedView ? "Archived notes" : "Notes"}
        description={
          archivedView
            ? `${archivedCount} archived note${archivedCount === 1 ? "" : "s"}`
            : totalNotes
              ? `${totalNotes} note${totalNotes === 1 ? "" : "s"} synced from your Pala`
              : "Notes synced from your Pala will appear here"
        }
        actions={archivedView ? undefined : <NotesSearch />}
      />

      {archivedView ? (
        <Link
          href="/dashboard/notes"
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
        >
          ← Back to notes
        </Link>
      ) : (totalNotes > 0 || archivedCount > 0) && !q ? (
        <div className="flex flex-wrap gap-1.5">
          <TagPill href="/dashboard/notes" label="All" active={!tag} />
          {tags.map((t) => (
            <TagPill
              key={t.tag}
              href={`/dashboard/notes?tag=${encodeURIComponent(t.tag)}`}
              label={t.tag}
              count={t.count}
              active={tag === t.tag}
            />
          ))}
          {archivedCount > 0 ? (
            <Link
              href="/dashboard/notes?view=archived"
              className="ml-auto inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
            >
              <Archive className="size-3" />
              Archived {archivedCount}
            </Link>
          ) : null}
        </div>
      ) : null}

      {q ? (
        <p className="text-sm text-muted-foreground">
          {notes.length} result{notes.length === 1 ? "" : "s"} for{" "}
          <span className="font-medium text-foreground">“{q}”</span>
        </p>
      ) : null}

      {notes.length === 0 ? (
        archivedView ? (
          <ArchivedEmpty />
        ) : totalNotes === 0 ? (
          <EmptyState />
        ) : (
          <NoResults />
        )
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {notes.map((note) => (
            <NoteCard key={note.id} note={note} archived={archivedView} tz={tz} />
          ))}
        </div>
      )}
    </div>
  );
}

function TagPill({
  href,
  label,
  count,
  active,
}: {
  href: string;
  label: string;
  count?: number;
  active: boolean;
}) {
  return (
    <Link
      href={href}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors",
        active
          ? "border-primary/30 bg-primary/10 text-primary"
          : "border-border bg-background text-muted-foreground hover:bg-accent hover:text-accent-foreground",
      )}
    >
      {label}
      {count != null ? <span className="opacity-60">{count}</span> : null}
    </Link>
  );
}

function NoteCard({ note, archived, tz }: { note: NoteSummary; archived: boolean; tz: string }) {
  return (
    <div className="group relative">
      <Link
        href={`/dashboard/notes/${note.id}`}
        className="flex flex-col gap-2 rounded-xl border bg-card p-4 transition-colors hover:border-primary/40 hover:bg-accent/40"
      >
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          {note.tag ? (
            <Badge variant="secondary" className="rounded-full font-medium">
              {note.tag}
            </Badge>
          ) : (
            <Badge variant="outline" className="rounded-full text-muted-foreground">
              untagged
            </Badge>
          )}
          <span>·</span>
          <span>#{note.note_num}</span>
          <span className="ml-auto pr-8">{formatNoteTime(note.created_utc, tz)}</span>
        </div>
        <p className="line-clamp-4 text-sm leading-relaxed text-foreground/90">
          {note.text_preview}
        </p>
      </Link>
      <div className="absolute right-2 top-2 opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100">
        <NoteArchiveButton noteId={note.id} archived={archived} />
      </div>
    </div>
  );
}

function ArchivedEmpty() {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed py-16 text-center">
      <div className="mb-4 flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
        <Archive className="size-6" />
      </div>
      <h3 className="text-base font-semibold">No archived notes</h3>
      <p className="mt-1 text-sm text-muted-foreground">
        Archive a note to tuck it away here. It won’t show in your list or to Claude.
      </p>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed py-16 text-center">
      <div className="mb-4 flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
        <Mic className="size-6" />
      </div>
      <h3 className="text-base font-semibold">No notes yet</h3>
      <p className="mt-1 max-w-sm text-sm text-muted-foreground">
        Record a note on your Pala and run a sync. Once your device is connected, transcripts
        land here automatically.
      </p>
      <Link
        href="/dashboard/devices"
        className="mt-5 inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90"
      >
        Set up your Pala
      </Link>
    </div>
  );
}

function NoResults() {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed py-16 text-center">
      <div className="mb-4 flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
        <FileText className="size-6" />
      </div>
      <h3 className="text-base font-semibold">Nothing here</h3>
      <p className="mt-1 text-sm text-muted-foreground">
        No notes match this filter. Try clearing it.
      </p>
    </div>
  );
}
