import Link from "next/link";
import { redirect } from "next/navigation";
import {
  Mic,
  RefreshCw,
  Bot,
  ArrowRight,
  Sparkles,
  Cpu,
  Lock,
  Plug,
} from "lucide-react";
import { auth } from "@/auth";
import { Button } from "@/components/ui/button";

export default async function Home() {
  const session = await auth();
  if (session?.user) redirect("/dashboard");

  return (
    <div className="flex min-h-screen flex-col">
      <SiteHeader />
      <main className="flex-1">
        <Hero />
        <HowItWorks />
        <Features />
        <CtaBand />
      </main>
      <SiteFooter />
    </div>
  );
}

function Logo() {
  return (
    <Link href="/" className="flex items-center gap-2">
      <span className="flex size-7 items-center justify-center rounded-md bg-primary text-primary-foreground">
        <span className="size-2 rounded-full bg-primary-foreground" />
      </span>
      <span className="text-[15px] font-semibold tracking-tight">Pala Note</span>
    </Link>
  );
}

function SiteHeader() {
  return (
    <header className="sticky top-0 z-50 border-b border-border/60 bg-background/80 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-5">
        <Logo />
        <nav className="hidden items-center gap-7 text-sm text-muted-foreground sm:flex">
          <a href="#how" className="transition-colors hover:text-foreground">How it works</a>
          <a href="#features" className="transition-colors hover:text-foreground">Features</a>
        </nav>
        <Button asChild size="sm">
          <Link href="/login">Sign in</Link>
        </Button>
      </div>
    </header>
  );
}

function Hero() {
  return (
    <section className="relative overflow-hidden">
      {/* coral glow */}
      <div
        aria-hidden
        className="pointer-events-none absolute left-1/2 top-[-10%] -z-10 size-[640px] -translate-x-1/2 rounded-full bg-primary/15 blur-3xl"
      />
      <div className="mx-auto max-w-6xl px-5 pb-16 pt-20 text-center sm:pt-28">
        <div className="mx-auto mb-5 inline-flex items-center gap-2 rounded-full border bg-background/60 px-3 py-1 text-xs font-medium text-muted-foreground">
          <Sparkles className="size-3.5 text-primary" />
          A pocket voice recorder that talks to Claude
        </div>
        <h1 className="mx-auto max-w-3xl text-balance text-4xl font-semibold tracking-tight sm:text-6xl">
          Speak it once.{" "}
          <span className="text-primary">Find it forever.</span>
        </h1>
        <p className="mx-auto mt-5 max-w-xl text-balance text-lg text-muted-foreground">
          Pala is a tiny e-paper device that captures your voice, transcribes it, and syncs every
          note to your private cloud — ready for Claude to search, summarize, and organize.
        </p>
        <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <Button asChild size="lg" className="gap-2">
            <Link href="/login">
              Get started — it&apos;s free <ArrowRight className="size-4" />
            </Link>
          </Button>
          <Button asChild variant="outline" size="lg">
            <a href="#how">See how it works</a>
          </Button>
        </div>
        <NotePreviewMock />
      </div>
    </section>
  );
}

function NotePreviewMock() {
  const notes = [
    { tag: "ideas", text: "App idea: a voice recorder that pipes straight into my notes and Claude." },
    { tag: "todo", text: "Call the dentist, renew domain, ship the landing page." },
    { tag: "journal", text: "Long walk this morning. Felt good to think out loud." },
  ];
  return (
    <div className="mx-auto mt-14 max-w-3xl">
      <div className="overflow-hidden rounded-2xl border bg-card shadow-xl shadow-primary/5">
        <div className="flex items-center gap-1.5 border-b bg-muted/40 px-4 py-3">
          <span className="size-3 rounded-full bg-destructive/40" />
          <span className="size-3 rounded-full bg-primary/40" />
          <span className="size-3 rounded-full bg-muted-foreground/30" />
          <span className="ml-3 text-xs text-muted-foreground">Pala Note — your notes</span>
        </div>
        <div className="grid gap-3 p-4 text-left sm:grid-cols-3">
          {notes.map((n) => (
            <div key={n.tag} className="rounded-xl border bg-background p-3.5">
              <span className="inline-block rounded-full bg-secondary px-2 py-0.5 text-xs font-medium text-secondary-foreground">
                {n.tag}
              </span>
              <p className="mt-2 text-sm leading-relaxed text-foreground/80">{n.text}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function HowItWorks() {
  const steps = [
    {
      icon: Mic,
      title: "Capture on Pala",
      desc: "Press the button and talk. The e-paper device records your voice anywhere — no phone needed.",
    },
    {
      icon: RefreshCw,
      title: "Sync & transcribe",
      desc: "On Wi-Fi, Pala transcribes with Whisper and uploads the text to your private workspace.",
    },
    {
      icon: Bot,
      title: "Ask Claude",
      desc: "Connect Claude over MCP to search, summarize, and act on your notes — guided by your instructions.",
    },
  ];
  return (
    <section id="how" className="border-t bg-muted/20 py-20">
      <div className="mx-auto max-w-6xl px-5">
        <SectionHeading eyebrow="How it works" title="From a thought to your second brain" />
        <div className="mt-12 grid gap-6 md:grid-cols-3">
          {steps.map((s, i) => (
            <div key={s.title} className="relative rounded-2xl border bg-card p-6">
              <span className="absolute right-5 top-5 text-sm font-semibold text-muted-foreground/40">
                0{i + 1}
              </span>
              <span className="flex size-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
                <s.icon className="size-5" />
              </span>
              <h3 className="mt-4 text-lg font-semibold">{s.title}</h3>
              <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">{s.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function Features() {
  const features = [
    { icon: Cpu, title: "Built for the device", desc: "Purpose-built firmware uploads transcripts the moment they're ready." },
    { icon: Sparkles, title: "Instructions for Claude", desc: "Set persistent context so Claude treats every note exactly how you want." },
    { icon: Bot, title: "Remote MCP", desc: "A secure MCP endpoint lets Claude list, search, and read your notes." },
    { icon: Lock, title: "Private by default", desc: "Each workspace is isolated. Keys are scoped and revocable any time." },
    { icon: Plug, title: "Integrations", desc: "Notion, Slack, Drive and more — pipe your notes wherever they belong." },
    { icon: RefreshCw, title: "Never lose a note", desc: "Deduplicated, retried uploads mean every recording reaches the cloud." },
  ];
  return (
    <section id="features" className="py-20">
      <div className="mx-auto max-w-6xl px-5">
        <SectionHeading eyebrow="Features" title="Everything your voice notes need" />
        <div className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((f) => (
            <div key={f.title} className="rounded-2xl border bg-card p-6">
              <span className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <f.icon className="size-5" />
              </span>
              <h3 className="mt-4 font-semibold">{f.title}</h3>
              <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">{f.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function CtaBand() {
  return (
    <section className="px-5 pb-20">
      <div className="relative mx-auto max-w-5xl overflow-hidden rounded-3xl border bg-primary px-6 py-14 text-center text-primary-foreground">
        <div aria-hidden className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(255,255,255,0.18),transparent_60%)]" />
        <h2 className="relative text-balance text-3xl font-semibold tracking-tight sm:text-4xl">
          Start capturing in minutes
        </h2>
        <p className="relative mx-auto mt-3 max-w-md text-balance text-primary-foreground/85">
          Sign in, flash your Pala from the browser, and your first note is one button-press away.
        </p>
        <Button asChild size="lg" variant="secondary" className="relative mt-7 gap-2">
          <Link href="/login">
            Get started <ArrowRight className="size-4" />
          </Link>
        </Button>
      </div>
    </section>
  );
}

function SectionHeading({ eyebrow, title }: { eyebrow: string; title: string }) {
  return (
    <div className="mx-auto max-w-2xl text-center">
      <div className="text-sm font-semibold uppercase tracking-wide text-primary">{eyebrow}</div>
      <h2 className="mt-2 text-balance text-3xl font-semibold tracking-tight sm:text-4xl">
        {title}
      </h2>
    </div>
  );
}

function SiteFooter() {
  return (
    <footer className="border-t py-8">
      <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-4 px-5 text-sm text-muted-foreground sm:flex-row">
        <Logo />
        <p>© {new Date().getFullYear()} Pala Note. Speak it once.</p>
        <Link href="/login" className="transition-colors hover:text-foreground">
          Sign in
        </Link>
      </div>
    </footer>
  );
}
