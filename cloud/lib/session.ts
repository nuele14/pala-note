import { redirect } from "next/navigation";
import { auth } from "@/auth";

export interface AppSession {
  tenantId: string;
  user: {
    name?: string | null;
    email?: string | null;
    image?: string | null;
  };
}

/**
 * Require an authenticated session with a tenant. Redirects to /login if the
 * visitor isn't signed in. Use at the top of every dashboard server component.
 */
export async function requireSession(): Promise<AppSession> {
  const session = await auth();
  const tenantId = session?.user?.tenantId;
  if (!session?.user || !tenantId) redirect("/login");
  return {
    tenantId,
    user: {
      name: session.user.name,
      email: session.user.email,
      image: session.user.image,
    },
  };
}
