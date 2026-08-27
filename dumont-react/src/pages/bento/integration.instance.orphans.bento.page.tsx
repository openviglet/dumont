import { SectionCardChromeProvider } from "@viglet/viglet-design-system/bento";
import IntegrationInstanceOrphansPage from "@/pages/integration.instance.orphans.page";

/**
 * The orphan-report review, in bento chrome (D70).
 *
 * The console page under a chrome declaration — the D47 shape in its read-only variant:
 * `AdaptivePageHeader` for the hero and `AdaptivePanel` for the containers, and no save bar,
 * because the two decisions this page offers are acts, not edits.
 */
export default function IntegrationInstanceOrphansBentoPage() {
  return (
    <SectionCardChromeProvider chrome="bento">
      <IntegrationInstanceOrphansPage />
    </SectionCardChromeProvider>
  );
}
