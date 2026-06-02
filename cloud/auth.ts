import NextAuth from "next-auth";
import GitHub from "next-auth/providers/github";
import { DrizzleAdapter } from "@auth/drizzle-adapter";
import { eq } from "drizzle-orm";
import { db } from "@/db";
import { users, tenants, accounts, sessions, verificationTokens } from "@/db/schema";

export const { handlers, auth, signIn, signOut } = NextAuth({
  // Trust the deployment host (Vercel domains, tunnels) so Auth.js builds
  // callback URLs from the request instead of requiring a hardcoded AUTH_URL.
  trustHost: true,
  adapter: DrizzleAdapter(db, {
    usersTable: users,
    accountsTable: accounts,
    sessionsTable: sessions,
    verificationTokensTable: verificationTokens,
  }),
  session: { strategy: "database" },
  pages: { signIn: "/login" },
  // OAuth only — no domain/SMTP needed. Auth.js reads AUTH_GITHUB_ID/SECRET
  // from the environment automatically.
  providers: [GitHub],
  events: {
    // Every new user gets its own tenant (MVP: 1 user = 1 tenant).
    async createUser({ user }) {
      if (!user.id) return;
      const [tenant] = await db
        .insert(tenants)
        .values({ name: user.email ?? "My workspace" })
        .returning({ id: tenants.id });
      await db
        .update(users)
        .set({ tenantId: tenant.id })
        .where(eq(users.id, user.id));
    },
  },
  callbacks: {
    // Expose tenantId on the session for server components / route handlers.
    async session({ session, user }) {
      const [row] = await db
        .select({ tenantId: users.tenantId })
        .from(users)
        .where(eq(users.id, user.id))
        .limit(1);
      session.user.tenantId = row?.tenantId ?? null;
      return session;
    },
  },
});
