import { requireSession } from "@/lib/session";
import { getWorkspace } from "@/lib/workspace";
import { Brand, SidebarNav } from "@/components/dashboard/sidebar-nav";
import { UserMenu } from "@/components/dashboard/user-menu";
import { MobileNav } from "@/components/dashboard/mobile-nav";

export default async function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { tenantId, user } = await requireSession();
  const workspace = await getWorkspace(tenantId);
  const workspaceName = workspace?.name || "My workspace";

  return (
    <div className="flex min-h-screen w-full">
      {/* Desktop sidebar */}
      <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col border-r bg-sidebar p-3 md:flex">
        <div className="mb-5 mt-1">
          <Brand />
        </div>
        <SidebarNav />
        <div className="mt-auto border-t pt-2">
          <UserMenu
            name={user.name}
            email={user.email}
            image={user.image}
            workspaceName={workspaceName}
          />
        </div>
      </aside>

      {/* Main column */}
      <div className="flex min-w-0 flex-1 flex-col">
        {/* Mobile top bar */}
        <header className="flex items-center gap-2 border-b bg-background px-4 py-2.5 md:hidden">
          <MobileNav />
          <Brand />
        </header>

        <main className="mx-auto w-full max-w-5xl flex-1 px-5 py-8 sm:px-8">
          {children}
        </main>
      </div>
    </div>
  );
}
