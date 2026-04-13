import { IconAdjustmentsSearch, IconChartBar, IconDatabase, IconFolder, IconGitCommit, IconGlobe, IconGraph, IconInfoCircle, IconListCheck, IconSparkles, IconTools } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Navigate, Route, Routes } from "react-router-dom";
import { Page } from "./components/page";
import IntegrationInstanceAssetsSourceListPage from "./pages/integration.instance.assets.source.list.page";
import IntegrationInstanceAssetsSourcePage from "./pages/integration.instance.assets.source.page";
import IntegrationInstanceDbSourceListPage from "./pages/integration.instance.db.source.list.page";
import IntegrationInstanceDbSourcePage from "./pages/integration.instance.db.source.page";
import IntegrationInstanceDoubleCheckPage from "./pages/integration.instance.double.check.page";
import IntegrationInstanceIndexingManagerPage from "./pages/integration.instance.indexing.manager.page";
import IntegrationInstanceIndexingRulesListPage from "./pages/integration.instance.indexing.rules.list.page";
import IntegrationInstanceIndexingRulesPage from "./pages/integration.instance.indexing.rules.page";
import IntegrationInstanceIndexingStatsPage from "./pages/integration.instance.indexing.stats.page";
import IntegrationInstanceInsightsPage from "./pages/integration.instance.insights.page";
import IntegrationInstanceMonitoringPage from "./pages/integration.instance.monitoring.page";
import IntegrationInstanceSourceListPage from "./pages/integration.instance.source.list.page";
import IntegrationInstanceSourceModelPage from "./pages/integration.instance.source.model.page";
import IntegrationInstanceSourcePage from "./pages/integration.instance.source.page";
import IntegrationInstanceSourceSpecPage from "./pages/integration.instance.source.spec.page";
import IntegrationInstanceSystemInfoPage from "./pages/integration.instance.system-info.page";
import IntegrationInstanceWcSourceListPage from "./pages/integration.instance.wc.source.list.page";
import IntegrationInstanceWcSourcePage from "./pages/integration.instance.wc.source.page";

// All dumont routes are defined here.
// The host (turing-react) mounts this via a single wildcard route.
// Adding new pages here requires NO changes in turing-react.
// Visibility is controlled by the sidebar (manifest.ts provider field),
// not by conditionally rendering routes.
export default function DumontRoutes() {
  const { t } = useTranslation();
  return (
    <Routes>
      {/* AEM Sources */}
      <Route path="source" element={<Page turIcon={IconGitCommit} title={t("integration.sources.title")} urlBase="source" />}>
        <Route index element={<IntegrationInstanceSourceListPage />} />
        <Route path=":sourceId/specifications/:specIndex" element={<IntegrationInstanceSourceSpecPage />} />
        <Route path=":sourceId/models/:modelIndex" element={<IntegrationInstanceSourceModelPage />} />
        <Route path=":sourceId/:tab" element={<IntegrationInstanceSourcePage />} />
        <Route path=":sourceId" element={<IntegrationInstanceSourcePage />} />
      </Route>
      {/* DB Sources */}
      <Route path="db-source" element={<Page turIcon={IconDatabase} title={t("integration.nav.dbSources")} urlBase="db-source" />}>
        <Route index element={<IntegrationInstanceDbSourceListPage />} />
        <Route path=":sourceId" element={<IntegrationInstanceDbSourcePage />} />
        <Route path=":sourceId/:tab" element={<IntegrationInstanceDbSourcePage />} />
      </Route>
      {/* WC Sources */}
      <Route path="wc-source" element={<Page turIcon={IconGlobe} title={t("integration.nav.wcSources")} urlBase="wc-source" />}>
        <Route index element={<IntegrationInstanceWcSourceListPage />} />
        <Route path=":sourceId" element={<IntegrationInstanceWcSourcePage />} />
        <Route path=":sourceId/:tab" element={<IntegrationInstanceWcSourcePage />} />
      </Route>
      {/* Assets Sources */}
      <Route path="assets-source" element={<Page turIcon={IconFolder} title={t("integration.nav.assetsSources")} urlBase="assets-source" />}>
        <Route index element={<IntegrationInstanceAssetsSourceListPage />} />
        <Route path=":sourceId" element={<IntegrationInstanceAssetsSourcePage />} />
        <Route path=":sourceId/:tab" element={<IntegrationInstanceAssetsSourcePage />} />
      </Route>
      {/* Indexing Rules */}
      <Route path="indexing-rule" element={<Page turIcon={IconTools} title={t("integration.nav.indexingRules")} urlBase="indexing-rule" />}>
        <Route index element={<IntegrationInstanceIndexingRulesListPage />} />
        <Route path=":ruleId" element={<IntegrationInstanceIndexingRulesPage />} />
      </Route>
      {/* Indexing Manager */}
      <Route path="indexing-manager" element={<Page turIcon={IconAdjustmentsSearch} title={t("integration.nav.indexingManager")} urlBase="indexing-manager" />}>
        <Route index element={<IntegrationInstanceIndexingManagerPage />} />
        <Route path=":mode" element={<IntegrationInstanceIndexingManagerPage />} />
      </Route>
      {/* Monitoring */}
      <Route path="monitoring" element={<Page turIcon={IconGraph} title={t("integration.nav.monitoring")} urlBase="monitoring" />}>
        <Route index element={<Navigate to="all" replace />} />
        <Route path=":source" element={<IntegrationInstanceMonitoringPage />} />
      </Route>
      {/* Indexing Stats */}
      <Route path="indexing-stats" element={<Page turIcon={IconChartBar} title={t("integration.nav.indexingStats")} urlBase="indexing-stats" />}>
        <Route index element={<IntegrationInstanceIndexingStatsPage />} />
      </Route>
      {/* Double Check */}
      <Route path="double-check" element={<Page turIcon={IconListCheck} title={t("integration.nav.doubleCheck")} urlBase="double-check" />}>
        <Route index element={<IntegrationInstanceDoubleCheckPage />} />
      </Route>
      {/* AI Insights */}
      <Route path="insights" element={<Page turIcon={IconSparkles} title={t("integration.nav.insights")} urlBase="insights" />}>
        <Route index element={<IntegrationInstanceInsightsPage />} />
      </Route>
      {/* System Information */}
      <Route path="system-info" element={<Page turIcon={IconInfoCircle} title={t("integration.nav.systemInfo")} urlBase="system-info" />}>
        <Route index element={<IntegrationInstanceSystemInfoPage />} />
      </Route>
    </Routes>
  );
}
