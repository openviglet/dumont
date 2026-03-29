import { Navigate, Route, Routes } from "react-router-dom";
import IntegrationInstanceSourceListPage from "./pages/integration.instance.source.list.page";
import IntegrationInstanceSourcePage from "./pages/integration.instance.source.page";
import IntegrationInstanceSourceSpecPage from "./pages/integration.instance.source.spec.page";
import IntegrationInstanceSourceModelPage from "./pages/integration.instance.source.model.page";
import IntegrationInstanceIndexingRulesListPage from "./pages/integration.instance.indexing.rules.list.page";
import IntegrationInstanceIndexingRulesPage from "./pages/integration.instance.indexing.rules.page";
import IntegrationInstanceIndexingManagerPage from "./pages/integration.instance.indexing.manager.page";
import IntegrationInstanceMonitoringPage from "./pages/integration.instance.monitoring.page";
import IntegrationInstanceIndexingStatsPage from "./pages/integration.instance.indexing.stats.page";
import IntegrationInstanceDoubleCheckPage from "./pages/integration.instance.double.check.page";
import IntegrationInstanceSystemInfoPage from "./pages/integration.instance.system-info.page";

// All dumont routes are defined here.
// The host (turing-react) mounts this via a single wildcard route.
// Adding new pages here requires NO changes in turing-react.
export default function DumontRoutes() {
  return (
    <Routes>
      {/* Sources */}
      <Route path="source" element={<IntegrationInstanceSourceListPage />} />
      <Route path="source/:sourceId" element={<IntegrationInstanceSourcePage />} />
      <Route path="source/:sourceId/:tab" element={<IntegrationInstanceSourcePage />} />
      <Route path="source/:sourceId/specifications/:specIndex" element={<IntegrationInstanceSourceSpecPage />} />
      <Route path="source/:sourceId/models/:modelIndex" element={<IntegrationInstanceSourceModelPage />} />
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
      {/* System Information */}
      <Route path="system-info" element={<IntegrationInstanceSystemInfoPage />} />
    </Routes>
  );
}
