"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Webhook, Trash2, Send, Eye, EyeOff } from "lucide-react";
import { toast } from "sonner";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { CopyButton } from "@/components/dashboard/copy-button";
import {
  createWebhook,
  deleteWebhook,
  sendTestWebhook,
  type WebhookView,
} from "@/app/dashboard/developers/actions";

function statusBadge(w: WebhookView) {
  if (w.lastStatus == null) return <Badge variant="outline" className="rounded-full">no deliveries</Badge>;
  if (w.lastStatus >= 200 && w.lastStatus < 300)
    return <Badge className="rounded-full bg-primary/15 text-primary hover:bg-primary/15">{w.lastStatus} ok</Badge>;
  return (
    <Badge variant="secondary" className="rounded-full text-destructive">
      {w.lastStatus === 0 ? "failed" : w.lastStatus}
    </Badge>
  );
}

export function WebhooksManager({ initial }: { initial: WebhookView[] }) {
  const router = useRouter();
  const [url, setUrl] = useState("");
  const [error, setError] = useState("");
  const [revealed, setRevealed] = useState<Record<string, boolean>>({});
  const [busy, start] = useTransition();

  function add() {
    setError("");
    start(async () => {
      const res = await createWebhook(url);
      if (!res.ok) {
        setError(res.error ?? "Couldn't add webhook.");
        return;
      }
      setUrl("");
      toast.success("Webhook added");
      router.refresh();
    });
  }

  function remove(id: string) {
    start(async () => {
      await deleteWebhook(id);
      toast.success("Webhook removed");
      router.refresh();
    });
  }

  function test(id: string) {
    start(async () => {
      const res = await sendTestWebhook(id);
      if (res.ok) toast.success(`Test delivered (HTTP ${res.status})`);
      else toast.error(`Test failed${res.status ? ` (HTTP ${res.status})` : ""}${res.error ? `: ${res.error}` : ""}`);
      router.refresh();
    });
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <span className="flex size-7 items-center justify-center rounded-md bg-primary/10 text-primary">
            <Webhook className="size-4" />
          </span>
          <CardTitle>Webhooks</CardTitle>
        </div>
        <CardDescription>
          We POST a <code className="rounded bg-muted px-1 py-0.5 text-xs">note.created</code> event
          to your endpoint whenever a note syncs. Verify the signature with the secret below.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-col gap-2 sm:flex-row">
          <Input
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://your-app.com/webhooks/pala"
          />
          <Button onClick={add} disabled={busy || !url.trim()} className="gap-1.5">
            <Webhook className="size-4" />
            Add endpoint
          </Button>
        </div>
        {error ? <p className="text-sm text-destructive">{error}</p> : null}

        {initial.length === 0 ? (
          <p className="rounded-lg border border-dashed py-6 text-center text-sm text-muted-foreground">
            No webhooks yet.
          </p>
        ) : (
          <ul className="divide-y rounded-xl border">
            {initial.map((w) => (
              <li key={w.id} className="space-y-2 p-3.5">
                <div className="flex items-center gap-2">
                  <span className="min-w-0 flex-1 truncate font-mono text-sm">{w.url}</span>
                  {w.pending > 0 ? (
                    <Badge variant="outline" className="rounded-full text-amber-600">
                      retrying ({w.pending})
                    </Badge>
                  ) : null}
                  {statusBadge(w)}
                </div>
                <div className="flex items-center gap-2">
                  <code className="flex-1 overflow-x-auto rounded-md border bg-muted/40 px-2 py-1 font-mono text-xs">
                    {revealed[w.id] ? w.secret : "whsec_" + "•".repeat(20)}
                  </code>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-8"
                    onClick={() => setRevealed((r) => ({ ...r, [w.id]: !r[w.id] }))}
                    aria-label="Reveal secret"
                  >
                    {revealed[w.id] ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                  </Button>
                  <CopyButton value={w.secret} label="Signing secret" />
                </div>
                {w.lastError ? (
                  <p className="text-xs text-destructive">last error: {w.lastError}</p>
                ) : null}
                <div className="flex items-center gap-2">
                  <Button variant="outline" size="sm" className="gap-1.5" onClick={() => test(w.id)} disabled={busy}>
                    <Send className="size-3.5" /> Send test
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="gap-1.5 text-muted-foreground hover:text-destructive"
                    onClick={() => remove(w.id)}
                    disabled={busy}
                  >
                    <Trash2 className="size-3.5" /> Delete
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
      <CardFooter className="border-t">
        <p className="text-xs text-muted-foreground">
          Deliveries time out after 5s and retry with exponential backoff (up to 6 attempts over
          ~9h). &quot;retrying&quot; shows queued attempts; the badge is the last delivery status.
        </p>
      </CardFooter>
    </Card>
  );
}
