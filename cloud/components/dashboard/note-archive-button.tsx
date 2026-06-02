"use client";

import { useTransition } from "react";
import { useRouter } from "next/navigation";
import { Archive, ArchiveRestore } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { setNoteArchived } from "@/app/dashboard/actions";
import { cn } from "@/lib/utils";

export function NoteArchiveButton({
  noteId,
  archived,
  variant = "icon",
}: {
  noteId: string;
  archived: boolean;
  variant?: "icon" | "button";
}) {
  const router = useRouter();
  const [busy, start] = useTransition();
  const Icon = archived ? ArchiveRestore : Archive;
  const label = archived ? "Unarchive" : "Archive";

  function toggle(e?: React.MouseEvent) {
    // When rendered over a clickable card, don't trigger the card's link.
    e?.preventDefault();
    e?.stopPropagation();
    start(async () => {
      await setNoteArchived(noteId, !archived);
      toast.success(archived ? "Note restored" : "Note archived");
      router.refresh();
    });
  }

  if (variant === "button") {
    return (
      <Button variant="outline" size="sm" className="gap-2" onClick={() => toggle()} disabled={busy}>
        <Icon className="size-4" />
        {busy ? "…" : label}
      </Button>
    );
  }

  return (
    <button
      type="button"
      onClick={toggle}
      disabled={busy}
      aria-label={label}
      title={label}
      className={cn(
        "flex size-7 items-center justify-center rounded-md border bg-background/90 text-muted-foreground shadow-sm backdrop-blur transition-colors hover:bg-accent hover:text-foreground",
      )}
    >
      <Icon className="size-3.5" />
    </button>
  );
}
