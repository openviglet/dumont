import { SectionCardChromeProvider } from "@viglet/viglet-design-system/bento";
import IntegrationInstanceSourceSpecPage from "@/pages/integration.instance.source.spec.page";

/**
 * One AEM attribute specification, in bento chrome (D60).
 *
 * The other editor behind the AEM source detail page, and unreachable from the shell for the same
 * reason: its route is three segments deep under `source`, and the bento tree served two.
 */
export default function IntegrationInstanceSourceSpecBentoPage() {
  return (
    <SectionCardChromeProvider chrome="bento">
      <IntegrationInstanceSourceSpecPage />
    </SectionCardChromeProvider>
  );
}
