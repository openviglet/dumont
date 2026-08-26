import { SectionCardChromeProvider } from "@viglet/viglet-design-system/bento";
import IntegrationInstanceMonitoringItemPage from "@/pages/integration.instance.monitoring.item.page";

/**
 * One monitored item, reached from the grid above it, in bento chrome (D58).
 *
 * The console page under a chrome declaration - the D47 shape in its read-only variant:
 * `AdaptivePageHeader` for the hero and `AdaptivePanel` for the containers, and no save bar,
 * because there is nothing on this page to save.
 */
export default function IntegrationInstanceMonitoringItemBentoPage() {
  return (
    <SectionCardChromeProvider chrome="bento">
      <IntegrationInstanceMonitoringItemPage />
    </SectionCardChromeProvider>
  );
}
