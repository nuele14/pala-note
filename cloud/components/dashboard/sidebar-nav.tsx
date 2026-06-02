"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { FileText, HardDrive, Blocks, Code2, Settings } from "lucide-react";
import { cn } from "@/lib/utils";

const NAV = [
  { href: "/dashboard/notes", label: "Notes", icon: FileText },
  { href: "/dashboard/devices", label: "Devices", icon: HardDrive },
  { href: "/dashboard/integrations", label: "Integrations", icon: Blocks },
  { href: "/dashboard/developers", label: "Developers", icon: Code2 },
  { href: "/dashboard/settings", label: "Settings", icon: Settings },
];

export function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();

  return (
    <nav className="flex flex-col gap-0.5">
      {NAV.map(({ href, label, icon: Icon }) => {
        const active = pathname === href || pathname.startsWith(href + "/");
        return (
          <Link
            key={href}
            href={href}
            onClick={onNavigate}
            className={cn(
              "flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm font-medium transition-colors",
              active
                ? "bg-accent text-accent-foreground"
                : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
            )}
          >
            <Icon className={cn("size-4 shrink-0", active && "text-primary")} />
            {label}
          </Link>
        );
      })}
    </nav>
  );
}

export function Brand() {
  return (
    <Link href="/dashboard/notes" className="flex items-center gap-2 px-1.5">
      <span className="flex size-7 items-center justify-center rounded-md bg-primary text-primary-foreground">
        <span className="size-2 rounded-full bg-primary-foreground" />
      </span>
      <span className="text-[15px] font-semibold tracking-tight">Pala Note</span>
    </Link>
  );
}
