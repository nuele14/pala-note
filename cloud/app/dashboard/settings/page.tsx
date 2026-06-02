import { requireSession } from "@/lib/session";
import { getWorkspace } from "@/lib/workspace";
import { PageHeader } from "@/components/dashboard/page-header";
import { SettingsForm } from "@/components/dashboard/settings-form";

export default async function SettingsPage() {
  const { tenantId } = await requireSession();
  const workspace = await getWorkspace(tenantId);

  return (
    <div className="space-y-6">
      <PageHeader title="Settings" description="Your workspace." />
      <SettingsForm
        initialName={workspace?.name ?? "My workspace"}
        initialTimezone={workspace?.timezone ?? "America/Los_Angeles"}
      />
    </div>
  );
}
