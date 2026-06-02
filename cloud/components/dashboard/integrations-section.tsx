"use client";

import { useState, type ComponentType } from "react";
import {
  Bot,
  CheckSquare,
  Check,
  NotebookPen,
  MessageSquare,
  HardDriveDownload,
  Workflow,
  Calendar,
} from "lucide-react";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { ClaudeConfig } from "@/components/dashboard/claude-config";
import { TodoistConfig } from "@/components/dashboard/todoist-config";
import type { TodoistView } from "@/app/dashboard/integrations/actions";

type Active = "claude" | "todoist";

const SOON: { name: string; icon: ComponentType<{ className?: string }>; desc: string }[] = [
  { name: "Notion", icon: NotebookPen, desc: "Append notes to a database or page." },
  { name: "Slack", icon: MessageSquare, desc: "Post transcripts to a channel." },
  { name: "Google Drive", icon: HardDriveDownload, desc: "Save notes as docs." },
  { name: "Zapier", icon: Workflow, desc: "Trigger 6,000+ apps." },
  { name: "Calendar", icon: Calendar, desc: "Turn notes into events." },
];

function Tile({
  icon: Icon,
  name,
  desc,
  badge,
  onClick,
  disabled,
}: {
  icon: ComponentType<{ className?: string }>;
  name: string;
  desc: string;
  badge?: React.ReactNode;
  onClick?: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={cn(
        "flex flex-col gap-2 rounded-xl border bg-card p-4 text-left transition-colors",
        disabled
          ? "cursor-default opacity-60"
          : "cursor-pointer hover:border-primary/40 hover:bg-accent/40",
      )}
    >
      <div className="flex items-center gap-2.5">
        <span
          className={cn(
            "flex size-9 items-center justify-center rounded-lg",
            disabled ? "bg-muted text-muted-foreground" : "bg-primary/10 text-primary",
          )}
        >
          <Icon className="size-4" />
        </span>
        <span className="text-sm font-semibold">{name}</span>
        {badge ? <span className="ml-auto">{badge}</span> : null}
      </div>
      <p className="text-sm text-muted-foreground">{desc}</p>
    </button>
  );
}

export function IntegrationsSection({
  mcpUrl,
  instructions,
  todoist,
}: {
  mcpUrl: string;
  instructions: string;
  todoist: TodoistView;
}) {
  const [open, setOpen] = useState<Active | null>(null);

  return (
    <>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <Tile
          icon={Bot}
          name="Claude"
          desc="Read & search your notes from Claude over MCP."
          badge={
            <Badge className="gap-1 rounded-full bg-primary/15 text-primary hover:bg-primary/15">
              <Check className="size-3" /> Available
            </Badge>
          }
          onClick={() => setOpen("claude")}
        />
        <Tile
          icon={CheckSquare}
          name="Todoist"
          desc="Turn todo-tagged notes into Todoist tasks."
          badge={
            todoist.connected ? (
              <Badge className="gap-1 rounded-full bg-primary/15 text-primary hover:bg-primary/15">
                <Check className="size-3" /> Connected
              </Badge>
            ) : (
              <Badge variant="outline" className="rounded-full">
                Set up
              </Badge>
            )
          }
          onClick={() => setOpen("todoist")}
        />
        {SOON.map((s) => (
          <Tile
            key={s.name}
            icon={s.icon}
            name={s.name}
            desc={s.desc}
            disabled
            badge={
              <Badge variant="secondary" className="rounded-full text-xs">
                Soon
              </Badge>
            }
          />
        ))}
      </div>

      <Dialog open={open !== null} onOpenChange={(o) => !o && setOpen(null)}>
        <DialogContent className="max-h-[88vh] gap-5 overflow-y-auto p-7 sm:max-w-2xl">
          {open === "claude" ? (
            <ClaudeConfig mcpUrl={mcpUrl} initialInstructions={instructions} />
          ) : null}
          {open === "todoist" ? <TodoistConfig view={todoist} /> : null}
        </DialogContent>
      </Dialog>
    </>
  );
}
