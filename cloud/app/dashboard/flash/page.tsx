import { headers } from "next/headers";
import { requireSession } from "@/lib/session";
import {
  getStoredOpenAiKeyMask,
  getSavedWifiInfo,
  listDevices,
} from "@/app/dashboard/flash/actions";
import { PageHeader } from "@/components/dashboard/page-header";
import { FlashWizard } from "@/components/flash/flash-wizard";

export default async function FlashPage() {
  await requireSession();
  const host = (await headers()).get("host") ?? "pala-cloud.vercel.app";
  const [mask, wifi, devices] = await Promise.all([
    getStoredOpenAiKeyMask(),
    getSavedWifiInfo(),
    listDevices(),
  ]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Set up a Pala"
        description="Flash a new device right from your browser — no tools to install."
      />
      <FlashWizard
        host={host}
        hasStoredKey={!!mask}
        storedMask={mask}
        savedWifiSsid={wifi?.ssid ?? null}
        savedWifiSsid2={wifi?.ssid2 || null}
        devices={devices}
      />
    </div>
  );
}
