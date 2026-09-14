import IntegrationInstanceCoveragePage from "@/pages/integration.instance.coverage.page";

/**
 * Per-field coverage and the drift banner, in bento chrome (D58).
 *
 * The console page under a chrome declaration - the D47 shape in its read-only variant:
 * `AdaptivePageHeader` for the hero and `AdaptivePanel` for the containers, and no save bar,
 * because there is nothing on this page to save.
 */
export default function IntegrationInstanceCoverageBentoPage() {
  return (
    <IntegrationInstanceCoveragePage />
  );
}
