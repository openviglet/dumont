import IntegrationInstanceMonitoringPage from "@/pages/integration.instance.monitoring.page";

/**
 * The indexing monitor - a filter strip over a paginated grid, in bento chrome (D58).
 *
 * The console page under a chrome declaration - the D47 shape in its read-only variant:
 * `AdaptivePageHeader` for the hero and `AdaptivePanel` for the containers, and no save bar,
 * because there is nothing on this page to save.
 */
export default function IntegrationInstanceMonitoringBentoPage() {
  return (
    <IntegrationInstanceMonitoringPage />
  );
}
