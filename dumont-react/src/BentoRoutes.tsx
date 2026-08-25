import "@/lib/axios"; // Configure axios baseURL + CSRF when mounted as MF remote.

import { Route, Routes } from "react-router-dom";
import BentoAreaPage from "./app/bento/bento.area.page";
import BentoHomePage from "./app/bento/bento.home.page";
import IntegrationInstanceDbSourceListBentoPage from "./pages/bento/integration.instance.db.source.list.bento.page";

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
      {/* Migrated in D37 — its console original at `db-source` is untouched. */}
      <Route path="db-source" element={<IntegrationInstanceDbSourceListBentoPage />} />
    </Routes>
  );
}
