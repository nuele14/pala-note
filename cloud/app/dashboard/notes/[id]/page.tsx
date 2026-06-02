import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { requireSession } from "@/lib/session";
import { getNote } from "@/lib/notes-queries";
import { getWorkspace } from "@/lib/workspace";
import { formatNoteFull } from "@/lib/format";
import { Badge } from "@/components/ui/badge";
import { NoteArchiveButton } from "@/components/dashboard/note-archive-button";

export default async function NoteDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { tenantId } = await requireSession();
  const { id } = await params;
  const [note, workspace] = await Promise.all([getNote(tenantId, id), getWorkspace(tenantId)]);
  if (!note) notFound();
  const tz = workspace?.timezone ?? "America/Los_Angeles";

  return (
    <article className="mx-auto max-w-2xl space-y-6">
      <Link
        href="/dashboard/notes"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
      >
        <ArrowLeft className="size-4" />
        All notes
      </Link>

      <header className="space-y-3 border-b pb-5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            {note.tag ? (
              <Badge variant="secondary" className="rounded-full font-medium">
                {note.tag}
              </Badge>
            ) : (
              <Badge variant="outline" className="rounded-full">
                untagged
              </Badge>
            )}
            <span>·</span>
            <span>Note #{note.note_num}</span>
            {note.archived ? (
              <Badge variant="outline" className="rounded-full text-muted-foreground">
                archived
              </Badge>
            ) : null}
          </div>
          <NoteArchiveButton noteId={note.id} archived={note.archived} variant="button" />
        </div>
        <h1 className="text-xl font-semibold tracking-tight">
          {formatNoteFull(note.created_utc, tz)}
        </h1>
      </header>

      <div className="whitespace-pre-wrap text-[15px] leading-7 text-foreground/90">
        {note.text}
      </div>
    </article>
  );
}
