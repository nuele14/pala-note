"use client";

import { useState } from "react";
import { Usb, CheckCircle2, AlertTriangle, Loader2 } from "lucide-react";
import { ESPLoader, Transport } from "esptool-js";
import { Button } from "@/components/ui/button";
import { markDeviceFlashed } from "@/app/dashboard/flash/actions";
import {
  buildConfigBlob,
  PALA_CFG_PARTITION_SIZE,
  type PalaDeviceConfig,
} from "@/lib/pala-device-config";

interface ManifestPart {
  path: string;
  offset: number;
}
interface Manifest {
  chipFamily: string;
  version?: string;
  parts: ManifestPart[];
  palaConfig: { offset: number };
}

type Phase = "idle" | "connecting" | "flashing" | "done" | "error";

const FLASH_OPTS = {
  flashMode: "keep" as const,
  flashFreq: "keep" as const,
  flashSize: "keep" as const,
};

export function Flasher({
  config,
  deviceKeyId,
  onDone,
}: {
  config: PalaDeviceConfig;
  deviceKeyId?: string;
  onDone?: () => void;
}) {
  const [phase, setPhase] = useState<Phase>("idle");
  const [progress, setProgress] = useState(0);
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const [chip, setChip] = useState("");

  const supported =
    typeof navigator !== "undefined" && "serial" in navigator;

  async function fetchBin(path: string): Promise<Uint8Array> {
    const res = await fetch(path);
    if (!res.ok) throw new Error(`Missing firmware file: ${path} (${res.status})`);
    return new Uint8Array(await res.arrayBuffer());
  }

  async function flash() {
    setError("");
    setProgress(0);
    setPhase("connecting");
    let transport: Transport | null = null;
    try {
      // 1. Load the firmware manifest + parts.
      setStatus("Loading firmware…");
      const manifest: Manifest = await fetch("/firmware/manifest.json").then((r) => {
        if (!r.ok) throw new Error("Firmware manifest not found.");
        return r.json();
      });
      const parts = await Promise.all(
        manifest.parts.map(async (p) => ({ data: await fetchBin(p.path), address: p.offset })),
      );

      // 2. Append the per-user config blob at the config partition offset.
      const blob = buildConfigBlob(config);
      parts.push({ data: blob, address: manifest.palaConfig.offset });

      // 3. Connect over Web Serial.
      setStatus("Select your Pala’s serial port…");
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const port = await (navigator as any).serial.requestPort();
      transport = new Transport(port, true);
      const loader = new ESPLoader({
        transport,
        baudrate: 115200,
        terminal: {
          clean() {},
          writeLine(d: string) {
            setStatus(d);
          },
          write() {},
        },
      });
      setStatus("Connecting to the device…");
      const detected = await loader.main();
      setChip(detected);

      // 4. Flash everything.
      setPhase("flashing");
      const totalBytes = parts.reduce((n, p) => n + p.data.length, 0);
      const written = new Array(parts.length).fill(0);
      await loader.writeFlash({
        fileArray: parts,
        ...FLASH_OPTS,
        eraseAll: false,
        compress: true,
        reportProgress(fileIndex: number, writtenInFile: number) {
          written[fileIndex] = writtenInFile;
          const done = written.reduce((a, b) => a + b, 0);
          setProgress(Math.min(99, Math.round((done / totalBytes) * 100)));
        },
      });
      setProgress(100);
      setPhase("done");

      // Record the flash on the device record (best effort).
      if (deviceKeyId) {
        await markDeviceFlashed(deviceKeyId, manifest.version).catch(() => {});
      }
      onDone?.();
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      // User cancelled the port picker — not an error worth shouting about.
      if (/no port selected|cancelled/i.test(msg)) {
        setPhase("idle");
        return;
      }
      setError(msg);
      setPhase("error");
    } finally {
      try {
        await transport?.disconnect();
      } catch {
        /* ignore */
      }
    }
  }

  if (!supported) {
    return (
      <div className="flex items-start gap-3 rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
        <AlertTriangle className="mt-0.5 size-4 shrink-0" />
        <div>
          <p className="font-medium">This browser can’t flash devices</p>
          <p className="mt-0.5 text-amber-900/80">
            Web Serial is needed. Open this page in <strong>Chrome</strong> or{" "}
            <strong>Edge</strong> on a desktop, then plug in your Pala.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {phase === "done" ? (
        <div className="flex items-start gap-3 rounded-xl border border-primary/30 bg-primary/5 p-4">
          <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-primary" />
          <div className="text-sm">
            <p className="font-medium text-foreground">Your Pala is flashed and configured 🎉</p>
            <p className="mt-0.5 text-muted-foreground">
              Unplug it and power it on. Record a note, then run a sync — it’ll transcribe and
              appear in your dashboard automatically. {chip ? `(Flashed ${chip}.)` : ""}
            </p>
          </div>
        </div>
      ) : null}

      {phase === "error" ? (
        <div className="flex items-start gap-3 rounded-xl border border-destructive/40 bg-destructive/5 p-4 text-sm">
          <AlertTriangle className="mt-0.5 size-4 shrink-0 text-destructive" />
          <div>
            <p className="font-medium text-destructive">Flashing failed</p>
            <p className="mt-0.5 break-words text-muted-foreground">{error}</p>
          </div>
        </div>
      ) : null}

      {phase === "connecting" || phase === "flashing" ? (
        <div className="rounded-xl border bg-card p-4">
          <div className="flex items-center gap-2 text-sm font-medium">
            <Loader2 className="size-4 animate-spin text-primary" />
            {phase === "flashing" ? `Flashing… ${progress}%` : "Connecting…"}
          </div>
          {phase === "flashing" ? (
            <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-muted">
              <div
                className="h-full bg-primary transition-all"
                style={{ width: `${progress}%` }}
              />
            </div>
          ) : null}
          <p className="mt-2 truncate text-xs text-muted-foreground">{status}</p>
        </div>
      ) : null}

      {phase === "idle" || phase === "error" || phase === "done" ? (
        <Button onClick={flash} size="lg" className="gap-2">
          <Usb className="size-4" />
          {phase === "done" ? "Flash again" : "Connect & flash my Pala"}
        </Button>
      ) : null}

      <p className="text-xs text-muted-foreground">
        Plug your Pala in over USB and hold its <strong>BOOT</strong> button if prompted. We’ll
        write the firmware plus your Wi-Fi, OpenAI key, and a device key
        ({(PALA_CFG_PARTITION_SIZE / 1024).toFixed(0)} KB config) so it works on first boot.
      </p>
    </div>
  );
}
