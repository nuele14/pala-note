"use client";

import { useState, useTransition } from "react";
import Link from "next/link";
import { Check, KeyRound, Wifi, Cpu, Usb, ArrowRight, AlertTriangle } from "lucide-react";
import {
  verifyAndSaveOpenAiKey,
  getStoredOpenAiKey,
  provisionDeviceKey,
  rotateDeviceKey,
  saveWifi,
  getSavedWifi,
} from "@/app/dashboard/flash/actions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";
import { SecurityNote } from "@/components/dashboard/security-note";
import { Flasher } from "./flasher";
import type { PalaDeviceConfig } from "@/lib/pala-device-config";

const STEPS = [
  { title: "OpenAI key", icon: KeyRound },
  { title: "Wi-Fi", icon: Wifi },
  { title: "Device", icon: Cpu },
  { title: "Flash", icon: Usb },
];

interface Device {
  id: string;
  name: string;
  prefix: string;
}

export function FlashWizard({
  host,
  hasStoredKey,
  storedMask,
  savedWifiSsid,
  savedWifiSsid2,
  devices,
}: {
  host: string;
  hasStoredKey: boolean;
  storedMask: string | null;
  savedWifiSsid: string | null;
  savedWifiSsid2: string | null;
  devices: Device[];
}) {
  const [step, setStep] = useState(0);

  // collected config
  const [openaiKey, setOpenaiKey] = useState("");
  const [ssid, setSsid] = useState("");
  const [password, setPassword] = useState("");
  const [ssid2, setSsid2] = useState("");
  const [password2, setPassword2] = useState("");
  const [palaKey, setPalaKey] = useState("");
  const [deviceKeyId, setDeviceKeyId] = useState<string | undefined>(undefined);
  const [flashed, setFlashed] = useState(false);

  // step 0 (OpenAI)
  const [replaceKey, setReplaceKey] = useState(!hasStoredKey);
  const [keyInput, setKeyInput] = useState("");
  const [keyError, setKeyError] = useState("");

  // step 1 (Wi-Fi)
  const [useSavedWifi, setUseSavedWifi] = useState(!!savedWifiSsid);
  const [rememberWifi, setRememberWifi] = useState(true);
  const [showBackup, setShowBackup] = useState(false);

  // step 2 (Device)
  const [deviceMode, setDeviceMode] = useState<"new" | "existing">(
    devices.length ? "existing" : "new",
  );
  const [deviceName, setDeviceName] = useState("My Pala");
  const [existingId, setExistingId] = useState(devices[0]?.id ?? "");
  const [deviceError, setDeviceError] = useState("");

  const [busy, start] = useTransition();
  const isLocalhost = host.startsWith("localhost") || host.startsWith("127.");

  function continueOpenAi() {
    setKeyError("");
    start(async () => {
      if (replaceKey) {
        const res = await verifyAndSaveOpenAiKey(keyInput);
        if (!res.ok) {
          setKeyError(res.error ?? "Couldn't verify that key.");
          return;
        }
        setOpenaiKey(keyInput.trim());
      } else {
        const stored = await getStoredOpenAiKey();
        if (!stored) {
          setReplaceKey(true);
          setKeyError("No saved key found — please paste one.");
          return;
        }
        setOpenaiKey(stored);
      }
      setStep(1);
    });
  }

  function continueWifi() {
    start(async () => {
      if (useSavedWifi) {
        const saved = await getSavedWifi();
        if (!saved) {
          setUseSavedWifi(false);
          return;
        }
        setSsid(saved.ssid);
        setPassword(saved.password);
        setSsid2(saved.ssid2);
        setPassword2(saved.password2);
      } else {
        if (rememberWifi && ssid.trim()) {
          await saveWifi(ssid.trim(), password, ssid2.trim(), password2);
        }
        setSsid(ssid.trim());
        setSsid2(ssid2.trim());
      }
      setStep(2);
    });
  }

  function continueDevice() {
    setDeviceError("");
    start(async () => {
      if (deviceMode === "existing") {
        const res = await rotateDeviceKey(existingId);
        if ("error" in res) {
          setDeviceError(res.error);
          return;
        }
        setPalaKey(res.plaintext);
        setDeviceKeyId(res.id);
      } else {
        const res = await provisionDeviceKey(deviceName);
        setPalaKey(res.plaintext);
        setDeviceKeyId(res.id);
      }
      setStep(3);
    });
  }

  const config: PalaDeviceConfig = {
    ssid,
    password,
    ssid2,
    password2,
    openaiKey,
    palaKey,
    palaHost: host,
  };
  const wifiReady = useSavedWifi || ssid.trim().length > 0;

  return (
    <div className="space-y-6">
      {/* Stepper */}
      <ol className="flex items-center gap-2">
        {STEPS.map((s, i) => {
          const done = i < step;
          const active = i === step;
          return (
            <li key={s.title} className="flex flex-1 items-center gap-2">
              <span
                className={cn(
                  "flex size-8 shrink-0 items-center justify-center rounded-full border text-sm font-medium",
                  done && "border-primary bg-primary text-primary-foreground",
                  active && "border-primary text-primary",
                  !done && !active && "border-border text-muted-foreground",
                )}
              >
                {done ? <Check className="size-4" /> : i + 1}
              </span>
              <span
                className={cn(
                  "hidden text-sm sm:inline",
                  active ? "font-medium text-foreground" : "text-muted-foreground",
                )}
              >
                {s.title}
              </span>
              {i < STEPS.length - 1 ? (
                <span className="mx-1 hidden h-px flex-1 bg-border sm:block" />
              ) : null}
            </li>
          );
        })}
      </ol>

      <div className="rounded-xl border bg-card p-6">
        {step === 0 ? (
          <Step
            title="Connect OpenAI"
            desc="Your Pala transcribes recordings with OpenAI Whisper. We verify the key, store it encrypted, and flash it onto the device."
          >
            {hasStoredKey && !replaceKey ? (
              <div className="flex items-center justify-between rounded-lg border bg-muted/40 px-3 py-2.5 text-sm">
                <span className="flex items-center gap-2">
                  <Check className="size-4 text-primary" />
                  Using saved key{" "}
                  <code className="font-mono text-muted-foreground">{storedMask}</code>
                </span>
                <button
                  type="button"
                  onClick={() => setReplaceKey(true)}
                  className="text-xs text-primary hover:underline"
                >
                  Use a different key
                </button>
              </div>
            ) : (
              <div className="grid gap-2">
                <Label htmlFor="openai">OpenAI API key</Label>
                <Input
                  id="openai"
                  type="password"
                  value={keyInput}
                  onChange={(e) => setKeyInput(e.target.value)}
                  placeholder="sk-…"
                  autoComplete="off"
                />
                {keyError ? <p className="text-sm text-destructive">{keyError}</p> : null}
                <p className="text-xs text-muted-foreground">
                  Find it at platform.openai.com/api-keys. We never expose it after saving.
                </p>
              </div>
            )}
            <StepActions>
              <Button onClick={continueOpenAi} disabled={busy} className="gap-2">
                {busy ? "Verifying…" : "Continue"}
                <ArrowRight className="size-4" />
              </Button>
            </StepActions>
          </Step>
        ) : null}

        {step === 1 ? (
          <Step
            title="Wi-Fi networks"
            desc="Your Pala uses Wi-Fi to transcribe and sync (2.4 GHz only — ESP32 can’t join 5 GHz). Add an optional backup network; the device connects to whichever is in range and you can switch the default on the device."
          >
            {savedWifiSsid && useSavedWifi ? (
              <div className="flex items-center justify-between rounded-lg border bg-muted/40 px-3 py-2.5 text-sm">
                <span className="flex items-center gap-2">
                  <Check className="size-4 text-primary" />
                  Using saved {savedWifiSsid2 ? "networks" : "network"}{" "}
                  <strong>{savedWifiSsid}</strong>
                  {savedWifiSsid2 ? (
                    <>
                      {" + "}
                      <strong>{savedWifiSsid2}</strong>
                    </>
                  ) : null}
                </span>
                <button
                  type="button"
                  onClick={() => setUseSavedWifi(false)}
                  className="text-xs text-primary hover:underline"
                >
                  Change
                </button>
              </div>
            ) : (
              <div className="grid gap-4 sm:max-w-sm">
                <div className="grid gap-2">
                  <Label htmlFor="ssid">Network name (SSID)</Label>
                  <Input id="ssid" value={ssid} onChange={(e) => setSsid(e.target.value)} />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="wifipass">Password</Label>
                  <Input
                    id="wifipass"
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete="off"
                  />
                </div>

                {showBackup ? (
                  <div className="grid gap-4 rounded-lg border border-dashed p-3">
                    <p className="text-xs font-medium text-muted-foreground">
                      Backup network (optional)
                    </p>
                    <div className="grid gap-2">
                      <Label htmlFor="ssid2">Network name (SSID)</Label>
                      <Input id="ssid2" value={ssid2} onChange={(e) => setSsid2(e.target.value)} />
                    </div>
                    <div className="grid gap-2">
                      <Label htmlFor="wifipass2">Password</Label>
                      <Input
                        id="wifipass2"
                        type="password"
                        value={password2}
                        onChange={(e) => setPassword2(e.target.value)}
                        autoComplete="off"
                      />
                    </div>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => setShowBackup(true)}
                    className="justify-self-start text-sm text-primary hover:underline"
                  >
                    + Add a backup network
                  </button>
                )}

                <label className="flex items-center gap-2 text-sm text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={rememberWifi}
                    onChange={(e) => setRememberWifi(e.target.checked)}
                    className="size-4 accent-primary"
                  />
                  Remember {showBackup ? "these networks" : "this network"} (encrypted) for next time
                </label>
              </div>
            )}
            <StepActions onBack={() => setStep(0)}>
              <Button onClick={continueWifi} disabled={busy || !wifiReady} className="gap-2">
                {busy ? "Saving…" : "Continue"} <ArrowRight className="size-4" />
              </Button>
            </StepActions>
          </Step>
        ) : null}

        {step === 2 ? (
          <Step
            title="Which Pala is this?"
            desc="Re-flashing a device rotates its key (the old one stops working). Setting up a new device creates a fresh key."
          >
            <div className="flex gap-2">
              <ModeButton
                active={deviceMode === "existing"}
                disabled={!devices.length}
                onClick={() => setDeviceMode("existing")}
                label="Existing device"
                hint={devices.length ? `${devices.length} registered` : "none yet"}
              />
              <ModeButton
                active={deviceMode === "new"}
                onClick={() => setDeviceMode("new")}
                label="New device"
                hint="create a key"
              />
            </div>

            <div className="mt-4">
              {deviceMode === "existing" ? (
                <div className="grid gap-2 sm:max-w-sm">
                  <Label>Device</Label>
                  <Select value={existingId} onValueChange={setExistingId}>
                    <SelectTrigger>
                      <SelectValue placeholder="Pick a device" />
                    </SelectTrigger>
                    <SelectContent>
                      {devices.map((d) => (
                        <SelectItem key={d.id} value={d.id}>
                          {d.name} ({d.prefix}…)
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <p className="text-xs text-muted-foreground">
                    Its key will be rotated — the device gets the new one when you flash.
                  </p>
                </div>
              ) : (
                <div className="grid gap-2 sm:max-w-sm">
                  <Label htmlFor="devname">Device name</Label>
                  <Input
                    id="devname"
                    value={deviceName}
                    onChange={(e) => setDeviceName(e.target.value)}
                    placeholder="My Pala"
                  />
                </div>
              )}
              {deviceError ? (
                <p className="mt-2 text-sm text-destructive">{deviceError}</p>
              ) : null}
            </div>

            <StepActions onBack={() => setStep(1)}>
              <Button
                onClick={continueDevice}
                disabled={busy || (deviceMode === "existing" && !existingId)}
                className="gap-2"
              >
                {busy
                  ? "Preparing…"
                  : deviceMode === "existing"
                    ? "Rotate key & continue"
                    : "Create key & continue"}
                <ArrowRight className="size-4" />
              </Button>
            </StepActions>
          </Step>
        ) : null}

        {step === 3 ? (
          <Step
            title="Flash your Pala"
            desc="Plug the device into this computer over USB, then connect and flash. This writes the firmware plus everything above."
          >
            {isLocalhost ? (
              <div className="mb-4 flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
                <AlertTriangle className="mt-0.5 size-4 shrink-0" />
                <span>
                  You’re on <code className="font-mono">{host}</code>. The device can’t reach a
                  localhost URL — flash from your deployed domain so uploads work.
                </span>
              </div>
            ) : null}
            <Flasher
              config={config}
              deviceKeyId={deviceKeyId}
              onDone={() => setFlashed(true)}
            />
            {/* Once flashed, don't send the user back into the wizard. */}
            {flashed ? (
              <div className="flex justify-end pt-2">
                <Button asChild className="gap-2">
                  <Link href="/dashboard/devices">
                    Done — go to Devices <ArrowRight className="size-4" />
                  </Link>
                </Button>
              </div>
            ) : (
              <StepActions onBack={() => setStep(2)} />
            )}
          </Step>
        ) : null}
      </div>

      <SecurityNote />
    </div>
  );
}

function ModeButton({
  active,
  disabled,
  onClick,
  label,
  hint,
}: {
  active: boolean;
  disabled?: boolean;
  onClick: () => void;
  label: string;
  hint: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={cn(
        "flex-1 rounded-lg border p-3 text-left transition-colors disabled:opacity-40",
        active ? "border-primary bg-primary/5" : "border-border hover:bg-accent",
      )}
    >
      <div className={cn("text-sm font-medium", active && "text-primary")}>{label}</div>
      <div className="text-xs text-muted-foreground">{hint}</div>
    </button>
  );
}

function Step({
  title,
  desc,
  children,
}: {
  title: string;
  desc: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-5">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold">{title}</h2>
        <p className="text-sm text-muted-foreground">{desc}</p>
      </div>
      {children}
    </div>
  );
}

function StepActions({
  onBack,
  children,
}: {
  onBack?: () => void;
  children?: React.ReactNode;
}) {
  return (
    <div className="flex items-center justify-between pt-2">
      {onBack ? (
        <Button variant="ghost" onClick={onBack}>
          Back
        </Button>
      ) : (
        <span />
      )}
      {children}
    </div>
  );
}
