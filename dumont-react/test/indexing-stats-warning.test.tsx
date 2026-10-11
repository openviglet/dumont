/*
 * D130 — a complete run can still be unhealthy, and the run table has to say so.
 *
 * Before this, a source's alarm ("new postings fell 92% under the 7-day median") could only ride
 * on a note, so a complete run carrying it looked exactly like a healthy one. The connector now
 * stores warnings apart from notes; this pins that the console marks the run and shows them.
 */
import { screen } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"

import IntegrationInstanceIndexingStatsPage from "@/pages/integration.instance.indexing.stats.page"

import { answerGet, renderSurface } from "./support/harness"

const WARNING = "new postings fell 92% under the 7-day median"

function run(overrides: Record<string, unknown>) {
  return {
    id: 1,
    provider: "JOBS",
    source: "adzuna",
    operationType: "INDEX_ALL",
    startTime: "2026-10-10T10:00:00Z",
    endTime: "2026-10-10T10:01:00Z",
    documentCount: 12,
    documentsPerMinute: 12,
    environment: null,
    locale: null,
    sites: [],
    partial: false,
    notes: [],
    ...overrides,
  }
}

function renderStats(stats: unknown[]) {
  // The count route is listed first: the stats route is a prefix of it.
  answerGet({ "/stats/count": { adzuna: 12 }, "/stats": stats })
  return renderSurface(<IntegrationInstanceIndexingStatsPage />, {
    pattern: "/integration/:id/stats",
    path: "/integration/local/stats",
  })
}

beforeEach(() => {
  vi.restoreAllMocks()
})

describe("D130: a warned run is marked in the run table", () => {
  it("shows the warning mark and the warning on a complete run", async () => {
    renderStats([run({ warned: true, warnings: [WARNING] })])

    expect(await screen.findByText(WARNING)).toBeDefined()
    expect(screen.getByText("Warning")).toBeDefined()
    expect(screen.queryByText("Partial")).toBeNull()
  })

  it("shows no mark on a run that warned of nothing", async () => {
    renderStats([run({ notes: ["12 records"] })])

    expect(await screen.findByText("12 records")).toBeDefined()
    expect(screen.queryByText("Warning")).toBeNull()
  })
})
