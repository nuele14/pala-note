import Link from "next/link";
import { Usb } from "lucide-react";
import { requireSession } from "@/lib/session";
import { listDeviceDetails } from "@/app/dashboard/flash/actions";
import { PageHeader } from "@/components/dashboard/page-header";
import { DevicesManager } from "@/components/dashboard/devices-manager";
import { Button } from "@/components/ui/button";

export default async function DevicesPage() {
  await requireSession();
  const devices = await listDeviceDetails();

  return (
    <div className="space-y-6">
      <PageHeader
        title="Devices"
        description="Your Pala devices. Each uploads notes with its own ingest key."
        actions={
          <Button asChild className="gap-2">
            <Link href="/dashboard/flash">
              <Usb className="size-4" />
              Set up a Pala
            </Link>
          </Button>
        }
      />
      <DevicesManager initial={devices} />
      <p className="px-1 text-xs text-muted-foreground">
        Looking for a Claude/MCP key? Those live under{" "}
        <Link href="/dashboard/integrations" className="text-primary underline-offset-2 hover:underline">
          Integrations → Claude
        </Link>
        .
      </p>
    </div>
  );
}
