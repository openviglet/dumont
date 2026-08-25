/*
 * D42 — what a jsdom document is missing before a console page will mount in it.
 *
 * Every entry below is here because a real surface asked for it: the sidebar reads `matchMedia`
 * to decide mobile, Radix primitives observe their trigger's size and capture pointers, and
 * `next-themes` reads the media query on mount. jsdom implements none of them, so without this
 * file a test fails on the environment rather than on the page — which is the failure mode that
 * teaches people the suite is noise.
 *
 * Kept to polyfills and teardown. Nothing here fakes a Dumont behaviour: a test that needs a
 * service to answer mocks that service itself, in the test that makes the claim.
 */
import "@testing-library/react"
import { cleanup } from "@testing-library/react"
import { afterEach, beforeAll, vi } from "vitest"

// i18n with the real EN/PT resources, so an assertion can name the string a user reads instead
// of the key a developer typed. A suite asserting on `integration.dbSources.title` would pass
// against a translation file with the entry deleted.
import "@/i18n"

beforeAll(() => {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }),
  })

  globalThis.ResizeObserver ??= class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }

  globalThis.IntersectionObserver ??= class {
    readonly root = null
    readonly rootMargin = ""
    readonly thresholds: readonly number[] = []
    observe() {}
    unobserve() {}
    disconnect() {}
    takeRecords(): IntersectionObserverEntry[] {
      return []
    }
  } as unknown as typeof IntersectionObserver

  Element.prototype.scrollIntoView ??= vi.fn()
  Element.prototype.hasPointerCapture ??= () => false
  Element.prototype.setPointerCapture ??= () => {}
  Element.prototype.releasePointerCapture ??= () => {}
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})
