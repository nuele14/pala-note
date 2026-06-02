import { ShieldCheck } from "lucide-react";

const DEFAULT_POINTS = [
  "Credentials (Wi-Fi password, OpenAI key, Todoist token) are encrypted at rest with AES-256-GCM and scoped to your workspace — never visible to other users.",
  "Your Wi-Fi password and device key are written to your Pala over a local USB cable during flashing; they don't pass through anyone else's servers.",
  "Device keys are upload-only and can be revoked anytime from Devices. API keys are shown in full only once.",
];

/** Small, reusable disclosure of how we handle credentials. */
export function SecurityNote({ points = DEFAULT_POINTS }: { points?: string[] }) {
  return (
    <div className="rounded-xl border bg-muted/30 p-4">
      <div className="flex items-center gap-2 text-sm font-medium">
        <ShieldCheck className="size-4 text-primary" />
        How your credentials are protected
      </div>
      <ul className="mt-2 space-y-1.5 text-xs leading-relaxed text-muted-foreground">
        {points.map((p, i) => (
          <li key={i} className="flex gap-2">
            <span className="mt-1 size-1 shrink-0 rounded-full bg-muted-foreground/50" />
            {p}
          </li>
        ))}
      </ul>
    </div>
  );
}
