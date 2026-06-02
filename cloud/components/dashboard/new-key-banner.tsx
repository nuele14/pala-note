"use client";

import { useState } from "react";
import { Copy, Check, KeyRound } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";

export function NewKeyBanner({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      toast.success("Key copied to clipboard");
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error("Couldn't copy — select and copy manually");
    }
  }

  return (
    <div className="rounded-xl border border-primary/30 bg-primary/5 p-4">
      <div className="flex items-center gap-2 text-sm font-medium text-primary">
        <KeyRound className="size-4" />
        Copy your new key now — it won&apos;t be shown again
      </div>
      <div className="mt-3 flex items-center gap-2">
        <code className="flex-1 overflow-x-auto rounded-md border bg-background px-3 py-2 font-mono text-sm">
          {value}
        </code>
        <Button variant="outline" size="icon" onClick={copy} aria-label="Copy key">
          {copied ? <Check className="size-4 text-primary" /> : <Copy className="size-4" />}
        </Button>
      </div>
      <p className="mt-2 text-xs text-muted-foreground">
        Paste it into your Pala firmware (<code className="font-mono">PALA_API_KEY</code>) for a
        device key, or into your Claude MCP connection for an MCP key.
      </p>
    </div>
  );
}
