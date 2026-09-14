import IntegrationInstanceIndexingStatsPage from "@/pages/integration.instance.indexing.stats.page";

/**
 * Reindex summaries and the run log, in bento chrome (D58).
 *
 * The console page under a chrome declaration - the D47 shape in its read-only variant:
 * `AdaptivePageHeader` for the hero and `AdaptivePanel` for the containers, and no save bar,
 * because there is nothing on this page to save.
 */
export default function IntegrationInstanceIndexingStatsBentoPage() {
  return (
    <IntegrationInstanceIndexingStatsPage />
  );
}
