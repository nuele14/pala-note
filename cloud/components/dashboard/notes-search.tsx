"use client";

import { useState, useTransition } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Search, X } from "lucide-react";
import { Input } from "@/components/ui/input";

export function NotesSearch() {
  const router = useRouter();
  const params = useSearchParams();
  const [value, setValue] = useState(params.get("q") ?? "");
  const [, startTransition] = useTransition();

  function submit(next: string) {
    const sp = new URLSearchParams(params.toString());
    if (next) sp.set("q", next);
    else sp.delete("q");
    // Searching is global; drop any tag filter to avoid an empty intersection.
    sp.delete("tag");
    startTransition(() => router.push(`/dashboard/notes?${sp.toString()}`));
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        submit(value.trim());
      }}
      className="relative w-full sm:max-w-xs"
    >
      <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
      <Input
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="Search notes…"
        className="pl-8 pr-8"
      />
      {value ? (
        <button
          type="button"
          onClick={() => {
            setValue("");
            submit("");
          }}
          className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
          aria-label="Clear search"
        >
          <X className="size-4" />
        </button>
      ) : null}
    </form>
  );
}
