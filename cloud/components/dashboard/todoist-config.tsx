"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Check, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import {
  connectTodoist,
  syncTodoistMetadata,
  saveTodoistSettings,
  disconnectTodoist,
  type TodoistView,
} from "@/app/dashboard/integrations/actions";

const PROMPT_PLACEHOLDER = `e.g. Route anything about clients to the "Work" project and tag #followup.
Personal errands go to "Inbox". Keep titles short and action-first.`;

export function TodoistConfig({ view }: { view: TodoistView }) {
  const router = useRouter();
  const [token, setToken] = useState("");
  const [tokenError, setTokenError] = useState("");
  const [todoTag, setTodoTag] = useState(view.todoTag);
  const [customPrompt, setCustomPrompt] = useState(view.customPrompt);
  const [busy, start] = useTransition();

  function onConnect() {
    setTokenError("");
    start(async () => {
      const res = await connectTodoist(token);
      if (!res.ok) {
        setTokenError(res.error ?? "Couldn't connect.");
        return;
      }
      setToken("");
      toast.success("Todoist connected", { description: "Pulled your projects, sections and labels." });
      router.refresh();
    });
  }

  function onSync() {
    start(async () => {
      const res = await syncTodoistMetadata();
      if (!res.ok) {
        toast.error(res.error ?? "Sync failed");
        return;
      }
      toast.success("Projects, sections & labels synced");
      router.refresh();
    });
  }

  function onSaveSettings() {
    start(async () => {
      await saveTodoistSettings(customPrompt, todoTag);
      toast.success("Saved");
      router.refresh();
    });
  }

  function onDisconnect() {
    start(async () => {
      await disconnectTodoist();
      toast.success("Todoist disconnected");
      router.refresh();
    });
  }

  const settingsDirty = todoTag !== view.todoTag || customPrompt !== view.customPrompt;

  return (
    <>
      <DialogHeader>
        <DialogTitle className="flex items-center gap-2">
          Todoist
          {view.connected ? (
            <Badge className="gap-1 rounded-full bg-primary/15 text-primary hover:bg-primary/15">
              <Check className="size-3" /> Connected
            </Badge>
          ) : null}
        </DialogTitle>
        <DialogDescription>
          Notes tagged <code className="rounded bg-muted px-1 py-0.5 text-xs">{view.todoTag}</code>{" "}
          become Todoist tasks — auto-summarized and routed to the right project, section &amp; labels.
        </DialogDescription>
      </DialogHeader>

      {!view.connected ? (
        <div className="space-y-4">
          <div className="grid gap-2">
            <Label htmlFor="td-token">Todoist API token</Label>
            <Input
              id="td-token"
              type="password"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              placeholder="Paste your API token"
              autoComplete="off"
            />
            {tokenError ? <p className="text-sm text-destructive">{tokenError}</p> : null}
            <p className="text-xs text-muted-foreground">
              Todoist → Settings → Integrations → Developer → copy the API token.
            </p>
          </div>
          <div className="flex justify-end">
            <Button onClick={onConnect} disabled={busy || !token.trim()} size="sm">
              {busy ? "Connecting…" : "Connect Todoist"}
            </Button>
          </div>
        </div>
      ) : (
        <div className="space-y-5">
          <div className="flex items-center justify-between rounded-lg border bg-muted/30 px-3 py-2.5 text-sm">
            <span>
              <strong>{view.projectCount}</strong> projects · <strong>{view.sectionCount}</strong>{" "}
              sections · <strong>{view.labelCount}</strong> labels
              {view.syncedAt ? (
                <span className="text-muted-foreground">
                  {" "}
                  · synced{" "}
                  {new Date(view.syncedAt).toLocaleDateString("en-US", { month: "short", day: "numeric" })}
                </span>
              ) : null}
            </span>
            <Button variant="outline" size="sm" className="gap-1.5" onClick={onSync} disabled={busy}>
              <RefreshCw className="size-3.5" />
              Sync
            </Button>
          </div>

          <div className="grid max-w-xs gap-2">
            <Label htmlFor="td-tag">Trigger tag</Label>
            <Input id="td-tag" value={todoTag} onChange={(e) => setTodoTag(e.target.value)} />
            <p className="text-xs text-muted-foreground">
              Notes with this device tag are turned into tasks.
            </p>
          </div>

          <div className="grid gap-2">
            <Label htmlFor="td-prompt">Task instructions for the AI</Label>
            <Textarea
              id="td-prompt"
              value={customPrompt}
              onChange={(e) => setCustomPrompt(e.target.value)}
              placeholder={PROMPT_PLACEHOLDER}
              className="min-h-32 leading-relaxed"
            />
            <p className="text-xs text-muted-foreground">
              Extra guidance on how to summarize tasks and pick projects/sections/labels.
            </p>
          </div>

          <div className="flex items-center justify-between border-t pt-4">
            <Button
              variant="ghost"
              size="sm"
              className="text-muted-foreground hover:text-destructive"
              onClick={onDisconnect}
              disabled={busy}
            >
              Disconnect
            </Button>
            <Button size="sm" onClick={onSaveSettings} disabled={busy || !settingsDirty}>
              {busy ? "Saving…" : "Save settings"}
            </Button>
          </div>
        </div>
      )}
    </>
  );
}
