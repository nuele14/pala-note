"use client";

import { useState, useTransition } from "react";
import Link from "next/link";
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
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { renameWorkspace, saveTimezone } from "@/app/dashboard/actions";
import { TIMEZONES } from "@/lib/timezones";

export function SettingsForm({
  initialName,
  initialTimezone,
}: {
  initialName: string;
  initialTimezone: string;
}) {
  const [name, setName] = useState(initialName);
  const [savedName, setSavedName] = useState(initialName);
  const [tz, setTz] = useState(initialTimezone);
  const [pending, start] = useTransition();
  const [tzPending, startTz] = useTransition();
  const nameDirty = name.trim() !== savedName && name.trim().length > 0;

  function onSaveName() {
    start(async () => {
      await renameWorkspace(name);
      setSavedName(name.trim());
      toast.success("Workspace name updated");
    });
  }

  function onChangeTz(value: string) {
    setTz(value);
    startTz(async () => {
      await saveTimezone(value);
      toast.success("Timezone updated");
    });
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Workspace</CardTitle>
          <CardDescription>The name shown across your dashboard.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid max-w-sm gap-2">
            <Label htmlFor="ws-name">Name</Label>
            <Input
              id="ws-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="My workspace"
            />
          </div>
        </CardContent>
        <CardFooter className="justify-end border-t">
          <Button onClick={onSaveName} disabled={!nameDirty || pending} size="sm">
            {pending ? "Saving…" : "Save"}
          </Button>
        </CardFooter>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Timezone</CardTitle>
          <CardDescription>Note timestamps are shown in this timezone.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid max-w-sm gap-2">
            <Label>Timezone</Label>
            <Select value={tz} onValueChange={onChangeTz} disabled={tzPending}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {TIMEZONES.map((t) => (
                  <SelectItem key={t.value} value={t.value}>
                    {t.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Instructions for Claude</CardTitle>
          <CardDescription>
            Your persistent AI context now lives with the Claude integration.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button asChild variant="outline" size="sm">
            <Link href="/dashboard/integrations">Open Integrations → Claude</Link>
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
