import { SectionCardChromeProvider } from "@viglet/viglet-design-system/bento";
import IntegrationInstanceIndexingRulePage from "@/pages/integration.instance.indexing.rules.page";

/**
 * One indexing rule, in bento chrome (D59).
 *
 * The D47 shape exactly: the console page under a chrome declaration, its ~530-line form shared
 * rather than forked, with `AdaptiveFormHeader` and `AdaptiveSectionCard` reading which era they
 * are in. Save, cancel and delete resolve through `ROUTES.INTEGRATION_INSTANCE`, so they stay in
 * whichever shell the reader started in.
 */
export default function IntegrationInstanceIndexingRulesBentoPage() {
  return (
    <SectionCardChromeProvider chrome="bento">
      <IntegrationInstanceIndexingRulePage />
    </SectionCardChromeProvider>
  );
}
