import Link from "next/link";
import { signIn, auth } from "@/auth";
import { redirect } from "next/navigation";
import { Button } from "@/components/ui/button";

function GithubMark() {
  return (
    <svg viewBox="0 0 24 24" className="size-4" fill="currentColor" aria-hidden>
      <path d="M12 .5C5.73.5.5 5.73.5 12c0 5.08 3.29 9.39 7.86 10.91.58.11.79-.25.79-.56 0-.28-.01-1.02-.02-2-3.2.69-3.88-1.54-3.88-1.54-.52-1.33-1.28-1.69-1.28-1.69-1.05-.72.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.55-.29-5.23-1.28-5.23-5.69 0-1.26.45-2.29 1.19-3.1-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.18 1.18a11.1 11.1 0 0 1 5.8 0c2.21-1.49 3.18-1.18 3.18-1.18.63 1.59.23 2.76.11 3.05.74.81 1.19 1.84 1.19 3.1 0 4.42-2.69 5.39-5.25 5.68.41.36.78 1.06.78 2.14 0 1.55-.01 2.8-.01 3.18 0 .31.21.68.8.56C20.71 21.39 24 17.08 24 12 24 5.73 18.77.5 12 .5Z" />
    </svg>
  );
}

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ callbackUrl?: string }>;
}) {
  const session = await auth();
  const { callbackUrl } = await searchParams;
  // Only allow same-origin relative paths as a redirect target.
  const dest = callbackUrl && callbackUrl.startsWith("/") ? callbackUrl : "/dashboard";
  if (session?.user) redirect(dest);

  async function signInWith(provider: "github") {
    "use server";
    await signIn(provider, { redirectTo: dest });
  }

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center px-5">
      <div
        aria-hidden
        className="pointer-events-none absolute left-1/2 top-1/4 -z-10 size-[480px] -translate-x-1/2 rounded-full bg-primary/15 blur-3xl"
      />
      <Link href="/" className="mb-8 flex items-center gap-2">
        <span className="flex size-8 items-center justify-center rounded-md bg-primary text-primary-foreground">
          <span className="size-2.5 rounded-full bg-primary-foreground" />
        </span>
        <span className="text-lg font-semibold tracking-tight">Pala Note</span>
      </Link>

      <div className="w-full max-w-sm rounded-2xl border bg-card p-7 shadow-sm">
        <h1 className="text-xl font-semibold tracking-tight">Welcome back</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Sign in to manage your Pala, notes, and integrations.
        </p>

        <form action={signInWith.bind(null, "github")} className="mt-6">
          <Button type="submit" className="w-full gap-2" size="lg">
            <GithubMark />
            Continue with GitHub
          </Button>
        </form>

        <p className="mt-6 text-center text-xs text-muted-foreground">
          By continuing you agree to keep your voice notes private to your workspace.
        </p>
      </div>

      <Link
        href="/"
        className="mt-6 text-sm text-muted-foreground transition-colors hover:text-foreground"
      >
        ← Back to home
      </Link>
    </div>
  );
}
