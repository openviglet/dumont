import { SectionCardChromeProvider } from "@viglet/viglet-design-system/bento";
import IntegrationInstanceSourceInferencePage from "@/pages/integration.instance.source.inference.page";

/**
 * AI source inference, in bento chrome (D57).
 *
 * The seventh and last surface in the Sources section, so a reader who opens Sources from the rail
 * now stays in the shell whatever they click — the first section for which that is true.
 *
 * Same shape as the six detail pages: the console page under a chrome declaration. This one is a
 * wizard rather than a form, so its header is `AdaptivePageHeader` (no save bar to morph) and its
 * three step cards are `AdaptiveSectionCard`. Nothing about the inference, the field manifest or
 * the dry-run preview is duplicated.
 */
export default function IntegrationInstanceSourceInferenceBentoPage() {
  return (
    <SectionCardChromeProvider chrome="bento">
      <IntegrationInstanceSourceInferencePage />
    </SectionCardChromeProvider>
  );
}
