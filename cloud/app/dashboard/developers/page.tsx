import { requireSession } from "@/lib/session";
import { listWebhooks } from "@/app/dashboard/developers/actions";
import { PageHeader } from "@/components/dashboard/page-header";
import { WebhooksManager } from "@/components/dashboard/webhooks-manager";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

function Code({ children }: { children: string }) {
  return (
    <pre className="overflow-x-auto rounded-lg border bg-muted/40 p-3 text-xs leading-relaxed">
      <code className="font-mono">{children}</code>
    </pre>
  );
}

export default async function DevelopersPage() {
  await requireSession();
  const webhooks = await listWebhooks();

  return (
    <div className="space-y-6">
      <PageHeader
        title="Developers"
        description="Get a webhook whenever a note syncs — to build on top of your Pala notes."
      />

      <WebhooksManager initial={webhooks} />

      {/* Webhook reference */}
      <Card>
        <CardHeader>
          <CardTitle>Webhook reference</CardTitle>
          <CardDescription>Event payload and how to verify the signature.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="space-y-2">
            <h3 className="text-sm font-semibold">Payload (POST to your endpoint)</h3>
            <Code>{`Headers:
  X-Pala-Event: note.created
  X-Pala-Timestamp: 1735732800
  X-Pala-Signature: t=1735732800,v1=<hmac-sha256 hex>

Body:
{
  "id": "evt_...",
  "type": "note.created",
  "created_at": "2026-06-01T08:00:00.000Z",
  "data": {
    "note": {
      "id": "uuid",
      "note_num": 1,
      "tag": "todo",
      "text": "Remember to water the plants.",
      "created_utc": "2026-06-01T08:00:00Z",
      "device_id": "ESP32-ABC123"
    }
  }
}`}</Code>
          </div>

          <div className="space-y-2">
            <h3 className="text-sm font-semibold">Verify the signature (Node)</h3>
            <p className="text-sm text-muted-foreground">
              HMAC-SHA256 the string{" "}
              <code className="rounded bg-muted px-1 py-0.5 text-xs">timestamp + &quot;.&quot; + rawBody</code>{" "}
              with your endpoint&apos;s signing secret and compare to the{" "}
              <code className="rounded bg-muted px-1 py-0.5 text-xs">v1</code> value.
            </p>
            <Code>{`import crypto from "node:crypto";

function verify(rawBody, header, secret) {
  const parts = Object.fromEntries(header.split(",").map(p => p.split("=")));
  const expected = crypto
    .createHmac("sha256", secret)
    .update(\`\${parts.t}.\${rawBody}\`)
    .digest("hex");
  return crypto.timingSafeEqual(
    Buffer.from(expected),
    Buffer.from(parts.v1)
  );
}`}</Code>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
