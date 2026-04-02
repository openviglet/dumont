import { Navigate, Route, Routes } from "react-router-dom";
import IntegrationInstanceSourceListPage from "./pages/integration.instance.source.list.page";
import IntegrationInstanceSourcePage from "./pages/integration.instance.source.page";
import IntegrationInstanceSourceSpecPage from "./pages/integration.instance.source.spec.page";
import IntegrationInstanceSourceModelPage from "./pages/integration.instance.source.model.page";
import IntegrationInstanceDbSourceListPage from "./pages/integration.instance.db.source.list.page";
import IntegrationInstanceDbSourcePage from "./pages/integration.instance.db.source.page";
import IntegrationInstanceWcSourceListPage from "./pages/integration.instance.wc.source.list.page";
import IntegrationInstanceWcSourcePage from "./pages/integration.instance.wc.source.page";
import IntegrationInstanceAssetsSourceListPage from "./pages/integration.instance.assets.source.list.page";
import IntegrationInstanceAssetsSourcePage from "./pages/integration.instance.assets.source.page";
import IntegrationInstanceIndexingRulesListPage from "./pages/integration.instance.indexing.rules.list.page";
import IntegrationInstanceIndexingRulesPage from "./pages/integration.instance.indexing.rules.page";
import IntegrationInstanceIndexingManagerPage from "./pages/integration.instance.indexing.manager.page";
import IntegrationInstanceMonitoringPage from "./pages/integration.instance.monitoring.page";
import IntegrationInstanceIndexingStatsPage from "./pages/integration.instance.indexing.stats.page";
import IntegrationInstanceDoubleCheckPage from "./pages/integration.instance.double.check.page";
import IntegrationInstanceSystemInfoPage from "./pages/integration.instance.system-info.page";
import IntegrationInstanceInsightsPage from "./pages/integration.instance.insights.page";

// All dumont routes are defined here.
// The host (turing-react) mounts this via a single wildcard route.
// Adding new pages here requires NO changes in turing-react.
// Visibility is controlled by the sidebar (manifest.ts provider field),
// not by conditionally rendering routes.
export default function DumontRoutes() {
  return (
    <Routes>
      {/* AEM Sources */}
      <Route path="source" element={<IntegrationInstanceSourceListPage />} />
      <Route path="source/:sourceId" element={<IntegrationInstanceSourcePage />} />
      <Route path="source/:sourceId/:tab" element={<IntegrationInstanceSourcePage />} />
      <Route path="source/:sourceId/specifications/:specIndex" element={<IntegrationInstanceSourceSpecPage />} />
      <Route path="source/:sourceId/models/:modelIndex" element={<IntegrationInstanceSourceModelPage />} />
      {/* DB Sources */}
      <Route path="db-source" element={<IntegrationInstanceDbSourceListPage />} />
      <Route path="db-source/:sourceId" element={<IntegrationInstanceDbSourcePage />} />
      <Route path="db-source/:sourceId/:tab" element={<IntegrationInstanceDbSourcePage />} />
      {/* WC Sources */}
      <Route path="wc-source" element={<IntegrationInstanceWcSourceListPage />} />
      <Route path="wc-source/:sourceId" element={<IntegrationInstanceWcSourcePage />} />
      <Route path="wc-source/:sourceId/:tab" element={<IntegrationInstanceWcSourcePage />} />
      {/* Assets Sources */}
      <Route path="assets-source" element={<IntegrationInstanceAssetsSourceListPage />} />
      <Route path="assets-source/:sourceId" element={<IntegrationInstanceAssetsSourcePage />} />
      <Route path="assets-source/:sourceId/:tab" element={<IntegrationInstanceAssetsSourcePage />} />
      {/* Indexing Rules */}
      <Route path="indexing-rule" element={<IntegrationInstanceIndexingRulesListPage />} />
      <Route path="indexing-rule/:ruleId" element={<IntegrationInstanceIndexingRulesPage />} />
      {/* Indexing Manager */}
      <Route path="indexing-manager" element={<IntegrationInstanceIndexingManagerPage />} />
      <Route path="indexing-manager/:mode" element={<IntegrationInstanceIndexingManagerPage />} />
      {/* Monitoring */}
      <Route path="monitoring">
        <Route index element={<Navigate to="all" replace />} />
        <Route path=":source" element={<IntegrationInstanceMonitoringPage />} />
      </Route>
      {/* Indexing Stats */}
      <Route path="indexing-stats" element={<IntegrationInstanceIndexingStatsPage />} />
      {/* Double Check */}
      <Route path="double-check" element={<IntegrationInstanceDoubleCheckPage />} />
      {/* AI Insights */}
      <Route path="insights" element={<IntegrationInstanceInsightsPage />} />
      {/* System Information */}
      <Route path="system-info" element={<IntegrationInstanceSystemInfoPage />} />
    </Routes>
  );
}
