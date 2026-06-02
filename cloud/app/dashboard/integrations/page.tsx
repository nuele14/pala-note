import { headers } from "next/headers";
import { requireSession } from "@/lib/session";
import { getWorkspace } from "@/lib/workspace";
import { getTodoistView } from "@/app/dashboard/integrations/actions";
import { PageHeader } from "@/components/dashboard/page-header";
import { IntegrationsSection } from "@/components/dashboard/integrations-section";

export default async function IntegrationsPage() {
  const { tenantId } = await requireSession();
  const host = (await headers()).get("host") ?? "pala-cloud.vercel.app";
  const proto = host.startsWith("localhost") ? "http" : "https";
  const mcpUrl = `${proto}://${host}/mcp`;
  const [todoist, workspace] = await Promise.all([getTodoistView(), getWorkspace(tenantId)]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Integrations"
        description="Connect your notes to the tools you already use. Select one to configure it."
      />
      <IntegrationsSection
        mcpUrl={mcpUrl}
        instructions={workspace?.instructions ?? ""}
        todoist={todoist}
      />
    </div>
  );
}
