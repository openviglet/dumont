import { SectionCardChromeProvider } from "@viglet/viglet-design-system/bento";
import IntegrationInstanceInsightsPage from "@/pages/integration.instance.insights.page";

/**
 * The LLM summary of this connector, in bento chrome (D58).
 *
 * The console page under a chrome declaration - the D47 shape in its read-only variant:
 * `AdaptivePageHeader` for the hero and `AdaptivePanel` for the containers, and no save bar,
 * because there is nothing on this page to save.
 */
export default function IntegrationInstanceInsightsBentoPage() {
  return (
    <SectionCardChromeProvider chrome="bento">
      <IntegrationInstanceInsightsPage />
    </SectionCardChromeProvider>
  );
}
