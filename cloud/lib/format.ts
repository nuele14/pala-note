import { DEFAULT_TIMEZONE } from "@/lib/timezones";

/** Short note timestamp in the workspace timezone, e.g. "Jun 1, 2:08 PM PDT". */
export function formatNoteTime(iso: string | null, tz: string): string {
  if (!iso) return "No date";
  return new Date(iso).toLocaleString("en-US", {
    timeZone: tz || DEFAULT_TIMEZONE,
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZoneName: "short",
  });
}

/** Full note timestamp in the workspace timezone (detail page). */
export function formatNoteFull(iso: string | null, tz: string): string {
  if (!iso) return "No date";
  return new Date(iso).toLocaleString("en-US", {
    timeZone: tz || DEFAULT_TIMEZONE,
    weekday: "short",
    month: "long",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZoneName: "short",
  });
}
