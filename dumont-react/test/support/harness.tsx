/*
 * D42 — the two things every console surface needs before it will render, and nothing else.
 *
 * A Dumont page resolves a router param and an axios call before it paints. Those are the two
 * dependencies the suite has to supply, and supplying them badly is how a rendering suite becomes
 * a suite that tests its own mocks: a helper that stubs `useTranslation`, or renders a page's
 * children instead of the page, passes whatever the page becomes.
 *
 * So this does exactly two things. It mounts the surface under a real `MemoryRouter` at a real
 * path, so `useParams` returns what the route says and `NavLink` resolves. And it answers axios
 * from a table of URL fragments the test writes, so the page runs its own service class, its own
 * `useEffect`, and its own loading branch. i18n is the real one, initialised in `setup.ts` with
 * the shipped EN resources — assertions name the string a user reads.
 */
import { render, type RenderResult } from "@testing-library/react"
import axios from "axios"
import type { ReactElement } from "react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { vi } from "vitest"

/**
 * Answer `axios.get` from a table keyed by a fragment of the request URL. First match wins, so a
 * test can put a specific path above a general one.
 *
 * A URL nothing in the table matches **rejects**, on purpose. Returning an empty array instead
 * would let a page whose service URL changed keep rendering its blank slate, and the test would
 * still pass while asserting nothing about the page it names.
 */
export function answerGet(table: Record<string, unknown>) {
  return vi.spyOn(axios, "get").mockImplementation(async (url: string) => {
    for (const [fragment, data] of Object.entries(table)) {
      if (url.includes(fragment)) return { data, status: 200 } as never
    }
    throw new Error(
      `no stub for GET ${url} — add it to the table, or the surface is calling an endpoint this `
      + "test did not expect it to call",
    )
  })
}

/** Mount one surface at `path`, with the router matching `pattern`. */
export function renderSurface(
  ui: ReactElement,
  { pattern = "/", path = "/" }: { pattern?: string; path?: string } = {},
): RenderResult {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={pattern} element={ui} />
      </Routes>
    </MemoryRouter>,
  )
}
