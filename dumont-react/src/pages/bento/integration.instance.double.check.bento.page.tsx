import { SectionCardChromeProvider } from "@viglet/viglet-design-system/bento";
import IntegrationInstanceDoubleCheckPage from "@/pages/integration.instance.double.check.page";

/**
 * What the connector holds against what the index does, in bento chrome (D58).
 *
 * The console page under a chrome declaration - the D47 shape in its read-only variant:
 * `AdaptivePageHeader` for the hero and `AdaptivePanel` for the containers, and no save bar,
 * because there is nothing on this page to save.
 */
export default function IntegrationInstanceDoubleCheckBentoPage() {
  return (
    <SectionCardChromeProvider chrome="bento">
      <IntegrationInstanceDoubleCheckPage />
    </SectionCardChromeProvider>
  );
}
