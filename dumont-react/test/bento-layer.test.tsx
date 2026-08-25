/*
 * D36 — the first import, and the only question it exists to answer.
 *
 * Dumont takes 95 imports from the design system and, until this file, not one of them came from
 * `./bento`. The subpath has been published since 2026.3.3 and this console has never rendered any
 * of it, which is why three products that share a design system look unrelated.
 *
 * What follows is deliberately *not* a migration. No page changes era here; D37 builds the route
 * tree and the 39 surfaces move one at a time after it. This is the comparison every one of those
 * later tasks silently assumes has already been made: does the package render correctly inside
 * *this* application — its router, its i18n registration, its Tailwind build, its jsdom — and does
 * a bento surface still carry the affordance its console counterpart carries today?
 *
 * So the three surfaces below are the same three D42 pinned for the console era, in the same
 * order, asserted on the same affordances:
 *
 *   console.root.page      → BentoNavRail      the shell composes and offers Home
 *   db.source.list.page    → BentoListPage     a row per item, and a blank slate when there are none
 *   db.source.page         → BentoFormHero     the control that saves it
 *
 * Turing is the reason the comparison is worth writing down rather than eyeballing: its
 * `BentoListPage` takes a layout as a prop instead of fetching one, and adopting it naively
 * dropped an affordance on 16 surfaces before anyone noticed. An import that type-checks proves
 * the module resolved, not that the surface survived.
 */
import { screen, within } from "@testing-library/react"
import {
  BentoEntityTile,
  BentoFormHero,
  BentoListPage,
  BentoNavRail,
  type BentoNavGroup,
} from "@viglet/viglet-design-system/bento"
import { IconDatabase, IconPlugConnected } from "@tabler/icons-react"
import { describe, expect, it } from "vitest"

import { renderSurface } from "./support/harness"

const STANDALONE = "local"
const BENTO_ROOT = `/integration/${STANDALONE}/bento`

/** One connector source, in the shape the DB list page already receives from `/db/source`. */
interface Source {
  id: string
  name: string
  description: string
}

const SOURCES: Source[] = [
  { id: "1", name: "Products", description: "Catalogue rows" },
  { id: "2", name: "Customers", description: "CRM rows" },
]

/**
 * A nav declaration in the package's own shape, written out here rather than imported.
 *
 * Dumont has no shared declaration yet — that is D39, and it is a separate task precisely because
 * the rail and the palette must read the same array. Until it exists, this literal keeps the claim
 * honest: the rail renders *Dumont's* sections, not a fixture borrowed from another product.
 */
const NAV: BentoNavGroup[] = [
  {
    section: {
      id: "sources",
      labelKey: "sidebar.sources",
      icon: IconPlugConnected,
      areaRoute: `${BENTO_ROOT}/sources`,
    },
    items: [
      {
        id: "db-source",
        titleKey: "integration.dbSources.title",
        descriptionKey: "integration.dbSources.description",
        icon: IconDatabase,
        section: "sources",
        tone: "indigo",
        fallbackRoute: `/integration/${STANDALONE}/db-source`,
      },
    ],
  },
]

describe("D36: the bento shell chrome", () => {
  it("renders the rail, with Home and the section hub Dumont declared", () => {
    renderSurface(<BentoNavRail groups={NAV} homeRoute={BENTO_ROOT} homeLabel="Home" />, {
      pattern: "/integration/:id/bento",
      path: BENTO_ROOT,
    })

    // Home is what a shell owes every reader regardless of which connector plugins are active —
    // the same claim D42 makes about the console sidebar, so the two eras are comparable.
    const home = screen.getByRole("link", { name: /home/i })
    expect(home.getAttribute("href")).toBe(BENTO_ROOT)

    // And the rail listed the section, not the leaf. A rail that flattened Dumont's sections into
    // one entry per surface would render here and overflow on a console with 39 of them, which is
    // the console sidebar's failure and the reason the rail exists.
    const hub = screen.getByRole("link", { name: /sources/i })
    expect(hub.getAttribute("href")).toBe(`${BENTO_ROOT}/sources`)
  })
})

describe("D36: a bento list surface", () => {
  const listProps = {
    tryAgainUrl: `${BENTO_ROOT}/db-source`,
    heroIcon: IconDatabase,
    title: "Database",
    subtitle: "Sources this connector indexes",
    newRoute: `${BENTO_ROOT}/db-source/new`,
    newLabel: "New source",
    itemKey: (s: Source) => s.id,
    emptyTitle: "No sources yet",
    emptyDescription: "Add one to start indexing.",
  }

  it("shows a tile per source, carrying the name and description the console row does", () => {
    renderSurface(
      <BentoListPage<Source>
        {...listProps}
        items={SOURCES}
        renderTile={(source) => (
          <BentoEntityTile
            to={`${BENTO_ROOT}/db-source/${source.id}`}
            defaultIcon={IconDatabase}
            title={source.name}
            description={source.description}
          />
        )}
      />,
      { pattern: "/integration/:id/bento/db-source", path: `${BENTO_ROOT}/db-source` },
    )

    expect(screen.getByText("Products")).toBeDefined()
    expect(screen.getByText("Customers")).toBeDefined()

    // The description travels with the tile. Turing's regression was exactly this shape: the page
    // rendered, the titles were there, and a per-item affordance had quietly stopped being passed.
    expect(screen.getByText("Catalogue rows")).toBeDefined()

    // A tile is a link to the entity, not decoration — the mosaic replaces a table whose rows
    // navigated, and a mosaic that lost the navigation looks finished and goes nowhere.
    const tile = screen.getByText("Products").closest("a")
    expect(tile?.getAttribute("href")).toBe(`${BENTO_ROOT}/db-source/1`)
  })

  it("shows the blank slate, not an empty mosaic, when there are none", () => {
    // The branch nobody opens, and the one D42 pins for the console era. An empty bento list
    // renders a different tree entirely (the empty-state panel plus the dashed New tile), so a
    // surface that lost it looks identical to one still loading.
    renderSurface(
      <BentoListPage<Source> {...listProps} items={[]} renderTile={() => null} />,
      { pattern: "/integration/:id/bento/db-source", path: `${BENTO_ROOT}/db-source` },
    )

    expect(screen.getByText("No sources yet")).toBeDefined()
    expect(screen.getByText("Add one to start indexing.")).toBeDefined()

    // And the way out of the empty state is still offered.
    const create = screen.getByRole("link", { name: /new source/i })
    expect(create.getAttribute("href")).toBe(`${BENTO_ROOT}/db-source/new`)
  })

  it("gates on undefined rather than painting an empty list while loading", () => {
    // `items === undefined` is the package's loading contract, and it is not the same value as
    // `[]`. A page that defaulted its state to `[]` would render the blank slate to every reader
    // for the length of the request — a bug that only shows on a slow connector.
    renderSurface(
      <BentoListPage<Source> {...listProps} items={undefined} renderTile={() => null} />,
      { pattern: "/integration/:id/bento/db-source", path: `${BENTO_ROOT}/db-source` },
    )

    expect(screen.queryByText("No sources yet")).toBeNull()
  })
})

describe("D36: a bento form surface", () => {
  it("shows the control that saves it, in both halves of the morph", () => {
    renderSurface(
      <form>
        <BentoFormHero
          backTo={`${BENTO_ROOT}/db-source`}
          backLabel="Database"
          title="Products"
          subtitle="Catalogue rows"
          onCancel={() => {}}
          dirty
          stickyTitle="Products"
        />
      </form>,
      { pattern: "/integration/:id/bento/db-source/:sourceId", path: `${BENTO_ROOT}/db-source/7` },
    )

    // The save affordance is the whole point of a form page — D42 makes the same assertion about
    // the console one. `BentoFormHero` owns *both* copies (the hero button that fades out and the
    // sticky bar that fades in), so a page wired to only one of them is a page that stops being
    // submittable halfway down the scroll. Finding two is the guarantee, not an accident.
    const saves = screen.getAllByRole("button", { name: /save/i })
    expect(saves).toHaveLength(2)

    // Dirty enables them. Rendering a Save that can never be pressed is the same broken page as
    // rendering no Save at all.
    for (const save of saves) expect(save.hasAttribute("disabled")).toBe(false)

    // Both are plain submits posting the enclosing form. A button that defaulted to `type=button`
    // renders identically and silently does nothing.
    for (const save of saves) expect(save.getAttribute("type")).toBe("submit")

    // And the breadcrumb back to the list survived, which is the affordance a hand-assembled
    // eyebrow is most likely to drop.
    const eyebrow = screen.getByRole("link", { name: /database/i })
    expect(eyebrow.getAttribute("href")).toBe(`${BENTO_ROOT}/db-source`)
  })

  it("disables Save and marks the page when the title is missing", () => {
    renderSurface(
      <form>
        <BentoFormHero title="" onCancel={() => {}} dirty titleMissing stickyTitle="" />
      </form>,
      { pattern: "/integration/:id/bento/db-source/:sourceId", path: `${BENTO_ROOT}/db-source/new` },
    )

    // `titleMissing` beats `dirty`: a new source with no name must not be submittable, and the
    // console form refuses the same save today.
    for (const save of screen.getAllByRole("button", { name: /save/i })) {
      expect(save.hasAttribute("disabled")).toBe(true)
    }
  })
})

describe("D36: the layer inside Dumont's own environment", () => {
  it("resolves its i18n through the registration Dumont already makes", () => {
    // `registerVigTranslations` runs in `src/i18n`, which `setup.ts` imports. The rail's Home
    // button falls back to the package's `home.title` key when a product passes no label — if that
    // registration had not reached the bento layer this would render the raw key.
    renderSurface(<BentoNavRail groups={NAV} homeRoute={BENTO_ROOT} />, {
      pattern: "/integration/:id/bento",
      path: BENTO_ROOT,
    })

    const rail = screen.getByRole("navigation")
    expect(within(rail).queryByText("home.title")).toBeNull()
  })
})
