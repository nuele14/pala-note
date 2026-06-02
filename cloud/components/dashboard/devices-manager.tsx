"use client";

import { useState, useTransition } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { HardDrive, Plus, RefreshCw, Trash2, Usb } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { NewKeyBanner } from "@/components/dashboard/new-key-banner";
import {
  provisionDeviceKey,
  rotateDeviceKey,
  removeDevice,
  type DeviceView,
} from "@/app/dashboard/flash/actions";

function fmt(iso: string | null): string {
  if (!iso) return "never";
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

export function DevicesManager({ initial }: { initial: DeviceView[] }) {
  const router = useRouter();
  const [name, setName] = useState("");
  const [revealed, setRevealed] = useState<string | null>(null);
  const [busy, start] = useTransition();

  function add() {
    start(async () => {
      const res = await provisionDeviceKey(name || "My Pala");
      setRevealed(res.plaintext);
      setName("");
      toast.success("Device added — copy its key now");
      router.refresh();
    });
  }

  function rotate(id: string, dname: string) {
    start(async () => {
      const res = await rotateDeviceKey(id);
      if ("error" in res) {
        toast.error(res.error);
        return;
      }
      setRevealed(res.plaintext);
      toast.success(`Rotated key for ${dname} — old key stopped working`);
      router.refresh();
    });
  }

  function remove(id: string, dname: string) {
    start(async () => {
      await removeDevice(id);
      toast.success(`Removed ${dname}`);
      router.refresh();
    });
  }

  return (
    <div className="space-y-4">
      {revealed ? <NewKeyBanner value={revealed} /> : null}

      <div className="flex flex-col gap-2 rounded-xl border bg-card p-4 sm:flex-row sm:items-end">
        <div className="flex-1">
          <p className="text-sm font-medium">Add a device manually</p>
          <p className="text-xs text-muted-foreground">
            Creates a device + ingest key to paste into firmware yourself. To flash from the
            browser instead, use “Set up a Pala”.
          </p>
        </div>
        <Input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Device name (e.g. Kitchen Pala)"
          className="sm:max-w-52"
        />
        <Button onClick={add} disabled={busy} className="gap-1.5">
          <Plus className="size-4" /> Add device
        </Button>
      </div>

      {initial.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed py-12 text-center">
          <span className="mb-3 flex size-11 items-center justify-center rounded-full bg-primary/10 text-primary">
            <HardDrive className="size-5" />
          </span>
          <p className="text-sm font-medium">No devices yet</p>
          <p className="mt-1 text-sm text-muted-foreground">Flash a Pala to get started.</p>
          <Button asChild className="mt-4 gap-2">
            <Link href="/dashboard/flash">
              <Usb className="size-4" /> Set up a Pala
            </Link>
          </Button>
        </div>
      ) : (
        <ul className="divide-y rounded-xl border bg-card">
          {initial.map((d) => (
            <li key={d.id} className="flex items-center gap-3 p-4">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <HardDrive className="size-4" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="truncate text-sm font-medium">{d.name}</span>
                  {d.firmwareVersion ? (
                    <Badge variant="secondary" className="rounded-full text-xs font-normal">
                      fw {d.firmwareVersion}
                    </Badge>
                  ) : null}
                </div>
                <div className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-muted-foreground">
                  <code className="font-mono">{d.prefix}…</code>
                  {d.deviceId ? (
                    <>
                      <span>·</span>
                      <code className="font-mono">{d.deviceId}</code>
                    </>
                  ) : null}
                  <span>·</span>
                  <span>flashed {fmt(d.lastFlashedAt)}</span>
                  <span>·</span>
                  <span>{d.lastSeenAt ? `synced ${fmt(d.lastSeenAt)}` : "never synced"}</span>
                </div>
              </div>
              <Button
                variant="outline"
                size="sm"
                className="gap-1.5"
                onClick={() => rotate(d.id, d.name)}
                disabled={busy}
              >
                <RefreshCw className="size-3.5" /> Rotate
              </Button>
              <Button
                variant="ghost"
                size="sm"
                className="text-muted-foreground hover:text-destructive"
                onClick={() => remove(d.id, d.name)}
                disabled={busy}
              >
                <Trash2 className="size-3.5" />
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
