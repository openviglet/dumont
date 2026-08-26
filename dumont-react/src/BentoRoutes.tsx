import "@/lib/axios"; // Configure axios baseURL + CSRF when mounted as MF remote.

import { Navigate, Route, Routes } from "react-router-dom";
import BentoAreaPage from "./app/bento/bento.area.page";
import BentoHomePage from "./app/bento/bento.home.page";
import IntegrationInstanceAssetsSourceDetailBentoPage from "./pages/bento/integration.instance.assets.source.detail.bento.page";
import IntegrationInstanceCoverageBentoPage from "./pages/bento/integration.instance.coverage.bento.page";
import IntegrationInstanceDoubleCheckBentoPage from "./pages/bento/integration.instance.double.check.bento.page";
import IntegrationInstanceIndexingStatsBentoPage from "./pages/bento/integration.instance.indexing.stats.bento.page";
import IntegrationInstanceInsightsBentoPage from "./pages/bento/integration.instance.insights.bento.page";
import IntegrationInstanceMonitoringBentoPage from "./pages/bento/integration.instance.monitoring.bento.page";
import IntegrationInstanceMonitoringItemBentoPage from "./pages/bento/integration.instance.monitoring.item.bento.page";
import IntegrationInstancePluginsBentoPage from "./pages/bento/integration.instance.plugins.bento.page";
import IntegrationInstanceSystemInfoBentoPage from "./pages/bento/integration.instance.system-info.bento.page";
import IntegrationInstanceAssetsSourceListBentoPage from "./pages/bento/integration.instance.assets.source.list.bento.page";
import IntegrationInstanceCatalogSourceDetailBentoPage from "./pages/bento/integration.instance.catalog.source.detail.bento.page";
import IntegrationInstanceCatalogSourceListBentoPage from "./pages/bento/integration.instance.catalog.source.list.bento.page";
import IntegrationInstanceDbSourceDetailBentoPage from "./pages/bento/integration.instance.db.source.detail.bento.page";
import IntegrationInstanceDbSourceListBentoPage from "./pages/bento/integration.instance.db.source.list.bento.page";
import IntegrationInstanceEdsSourceDetailBentoPage from "./pages/bento/integration.instance.eds.source.detail.bento.page";
import IntegrationInstanceEdsSourceListBentoPage from "./pages/bento/integration.instance.eds.source.list.bento.page";
import IntegrationInstanceSourceDetailBentoPage from "./pages/bento/integration.instance.source.detail.bento.page";
import IntegrationInstanceSourceInferenceBentoPage from "./pages/bento/integration.instance.source.inference.bento.page";
import IntegrationInstanceSourceListBentoPage from "./pages/bento/integration.instance.source.list.bento.page";
import IntegrationInstanceWcSourceDetailBentoPage from "./pages/bento/integration.instance.wc.source.detail.bento.page";
import IntegrationInstanceWcSourceListBentoPage from "./pages/bento/integration.instance.wc.source.list.bento.page";

/**
 * The bento tree — the parallel router beside `DumontRoutes` (D37).
 *
 * A migration of 39 pages is only safe if it can be done one page at a time and undone the same
 * way, and that needs somewhere for a migrated surface to live that is *not* the console route
 * tree. This is that somewhere. `DumontRoutes` keeps every route it has; nothing is moved out of
 * it and nothing redirects into here.
 *
 * So a surface migrates by gaining a line below — and moves back by losing it. The console page
 * on the other tree is what it falls back to, still mounted, still serving, until the bento one is
 * trusted. That is the property Turing did not have: its bento layer grew in place, page by page,
 * until 118 pages depended on components nothing shared and unwinding it took a block of its own.
 *
 * Deliberately not a mirror of `DumontRoutes`. A route appears here when its page has actually
 * moved, so the tree can never advertise a surface it does not serve.
 */
export default function BentoRoutes() {
  return (
    <Routes>
      <Route index element={<BentoHomePage />} />
      {/*
       * One route for every section hub (D45). A section is data, so a new one is a line in
       * `DUMONT_NAV_SECTIONS` — never a page here, and never a route the rail can point at and
       * this tree cannot answer.
       */}
      <Route path="area/:sectionId" element={<BentoAreaPage />} />
      {/*
       * The source lists. Each console original is untouched and still serves; only the list has
       * moved, so a card still opens its detail page on the other tree until D47.
       */}
      <Route path="source">
        <Route index element={<IntegrationInstanceSourceListBentoPage />} />
        <Route path=":sourceId" element={<IntegrationInstanceSourceDetailBentoPage />} />
        <Route path=":sourceId/:tab" element={<IntegrationInstanceSourceDetailBentoPage />} />
      </Route>
      <Route path="db-source">
        <Route index element={<IntegrationInstanceDbSourceListBentoPage />} />
        <Route path=":sourceId" element={<IntegrationInstanceDbSourceDetailBentoPage />} />
        <Route path=":sourceId/:tab" element={<IntegrationInstanceDbSourceDetailBentoPage />} />
      </Route>
      <Route path="wc-source">
        <Route index element={<IntegrationInstanceWcSourceListBentoPage />} />
        <Route path=":sourceId" element={<IntegrationInstanceWcSourceDetailBentoPage />} />
        <Route path=":sourceId/:tab" element={<IntegrationInstanceWcSourceDetailBentoPage />} />
      </Route>
      <Route path="assets-source">
        <Route index element={<IntegrationInstanceAssetsSourceListBentoPage />} />
        <Route path=":sourceId" element={<IntegrationInstanceAssetsSourceDetailBentoPage />} />
        <Route path=":sourceId/:tab" element={<IntegrationInstanceAssetsSourceDetailBentoPage />} />
      </Route>
      <Route path="catalog-source">
        <Route index element={<IntegrationInstanceCatalogSourceListBentoPage />} />
        <Route path=":sourceId" element={<IntegrationInstanceCatalogSourceDetailBentoPage />} />
        <Route path=":sourceId/:tab" element={<IntegrationInstanceCatalogSourceDetailBentoPage />} />
      </Route>
      <Route path="eds-source">
        <Route index element={<IntegrationInstanceEdsSourceListBentoPage />} />
        <Route path=":sourceId" element={<IntegrationInstanceEdsSourceDetailBentoPage />} />
        <Route path=":sourceId/:tab" element={<IntegrationInstanceEdsSourceDetailBentoPage />} />
      </Route>
      {/* AI source inference (D57) — the seventh Sources surface, and the one that completes it. */}
      <Route path="source-inference" element={<IntegrationInstanceSourceInferenceBentoPage />} />
      {/*
       * The Integration section's read-only surfaces (D58) — panels, tables and counts, so each is
       * the console page under a `chrome="bento"` declaration and nothing about them is forked.
       *
       * Monitoring keeps the console tree's shape rather than a flattened one: the source segment
       * is what the tab strip reads, and the item routes are reached from the grid through
       * `ROUTES.INTEGRATION_INSTANCE`, which resolves to `/bento/…` from in here. Serving the grid
       * and not the item would put a dead link on a page that had just been migrated.
       */}
      <Route path="monitoring">
        <Route index element={<Navigate to="all" replace />} />
        <Route path=":source/:itemId/:tab" element={<IntegrationInstanceMonitoringItemBentoPage />} />
        <Route path=":source/:itemId" element={<IntegrationInstanceMonitoringItemBentoPage />} />
        <Route path=":source" element={<IntegrationInstanceMonitoringBentoPage />} />
      </Route>
      <Route path="indexing-stats" element={<IntegrationInstanceIndexingStatsBentoPage />} />
      <Route path="coverage" element={<IntegrationInstanceCoverageBentoPage />} />
      <Route path="double-check" element={<IntegrationInstanceDoubleCheckBentoPage />} />
      <Route path="insights" element={<IntegrationInstanceInsightsBentoPage />} />
      <Route path="system-info" element={<IntegrationInstanceSystemInfoBentoPage />} />
      <Route path="plugins" element={<IntegrationInstancePluginsBentoPage />} />
    </Routes>
  );
}
