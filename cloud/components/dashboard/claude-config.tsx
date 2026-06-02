"use client";

import { useState, useTransition } from "react";
import { Sparkles, Check } from "lucide-react";
import { toast } from "sonner";
import { DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { CopyButton } from "@/components/dashboard/copy-button";
import { saveInstructions } from "@/app/dashboard/actions";

const PLACEHOLDER = `e.g. You're my second brain. My Pala notes are quick voice memos, often messy.

- Treat notes tagged "todo" as action items.
- I'm a product designer — assume that context.
- When I ask for a summary, group by tag.`;

export function ClaudeConfig({
  mcpUrl,
  initialInstructions,
}: {
  mcpUrl: string;
  initialInstructions: string;
}) {
  const [instructions, setInstructions] = useState(initialInstructions);
  const [saved, setSaved] = useState(initialInstructions);
  const [busy, start] = useTransition();
  const dirty = instructions !== saved;

  function onSave() {
    start(async () => {
      await saveInstructions(instructions);
      setSaved(instructions);
      toast.success("Instructions saved", {
        description: "Claude will use these next time it reads your notes.",
      });
    });
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Claude</DialogTitle>
        <DialogDescription>
          Read and search your notes from Claude (or any MCP client) over a secure connection.
        </DialogDescription>
      </DialogHeader>

      <div className="space-y-5">
        <div className="space-y-2">
          <Label>MCP endpoint</Label>
          <div className="flex items-center gap-2">
            <code className="flex-1 overflow-x-auto rounded-md border bg-muted/40 px-3 py-2 font-mono text-sm">
              {mcpUrl}
            </code>
            <CopyButton value={mcpUrl} label="MCP URL" />
          </div>
          <p className="text-xs text-muted-foreground">
            Add this as an HTTP MCP server in Claude. You&apos;ll be sent here to{" "}
            <strong className="font-medium text-foreground">sign in with GitHub</strong> (OAuth) —
            no API key to paste.
          </p>
        </div>

        <div className="space-y-2 border-t pt-4">
          <div className="flex items-center gap-2">
            <span className="flex size-6 items-center justify-center rounded-md bg-primary/10 text-primary">
              <Sparkles className="size-3.5" />
            </span>
            <Label htmlFor="cl-instr" className="text-sm font-medium">
              Instructions for Claude
            </Label>
          </div>
          <p className="text-xs text-muted-foreground">
            Persistent context Claude reads (via the <code className="rounded bg-muted px-1 py-0.5">get_instructions</code> tool) before working with your notes.
          </p>
          <Textarea
            id="cl-instr"
            value={instructions}
            onChange={(e) => setInstructions(e.target.value)}
            placeholder={PLACEHOLDER}
            className="min-h-40 resize-y leading-relaxed"
          />
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
              {!dirty && saved ? (
                <>
                  <Check className="size-3.5 text-primary" /> Saved
                </>
              ) : (
                `${instructions.length.toLocaleString()} / 10,000`
              )}
            </span>
            <Button size="sm" onClick={onSave} disabled={!dirty || busy}>
              {busy ? "Saving…" : "Save instructions"}
            </Button>
          </div>
        </div>
      </div>
    </>
  );
}
