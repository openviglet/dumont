/*
 * D66 — the behaviour that has to survive removing the two synchronous writes.
 *
 * `useConnectorProvider` used to reset state on entry to the effect, which is what made `loading`
 * correct when the integration id changed: the reset cleared the previous id's answer before the
 * new request went out. Deleting the reset without keying the answer by its question would leave
 * the hook briefly reporting the *previous* instance's provider as the current one's — and pages
 * use that value to decide which source API to call, so the wrong answer is not cosmetic.
 *
 * So the contract goes in here: what `loading` and `provider` are before, during and after a
 * lookup, and across a mid-flight id change.
 */
import { renderHook, waitFor } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { useConnectorProvider } from "@/hooks/use-connector-provider"

const status = vi.fn()

vi.mock("@/services/integration/integration-connector.service", () => ({
  TurIntegrationConnectorService: class {
    constructor(private readonly integrationId: string) {}
    status() {
      return status(this.integrationId)
    }
  },
}))

/** A promise plus the handles to settle it, so a test can hold a lookup open. */
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe("useConnectorProvider (D66)", () => {
  beforeEach(() => {
    status.mockReset()
    // The module-level promise cache is shared across renders by design; a fresh module per test
    // is what keeps one test's answer out of the next one's.
    vi.resetModules()
  })

  it("reports loading until the answer for this id arrives", async () => {
    const pending = deferred<{ provider: string }>()
    status.mockReturnValue(pending.promise)

    const { result } = renderHook(() => useConnectorProvider("i-1"))

    expect(result.current).toEqual({ provider: null, loading: true })

    pending.resolve({ provider: "AEM" })
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.provider).toBe("AEM")
  })

  it("is not loading, and has no provider, when there is no id to ask about", () => {
    const { result } = renderHook(() => useConnectorProvider(undefined))

    // Nothing to wait for: the old hook reported loading on the first render and corrected
    // itself in the effect, which is the flicker the derivation removes.
    expect(result.current).toEqual({ provider: null, loading: false })
    expect(status).not.toHaveBeenCalled()
  })

  it("never reports the previous id's provider when the id changes mid-flight", async () => {
    const first = deferred<{ provider: string }>()
    const second = deferred<{ provider: string }>()
    status.mockImplementation((id: string) =>
      id === "i-1" ? first.promise : second.promise,
    )

    const { result, rerender } = renderHook(
      ({ id }: { id: string }) => useConnectorProvider(id),
      { initialProps: { id: "i-1" } },
    )

    first.resolve({ provider: "AEM" })
    await waitFor(() => expect(result.current.provider).toBe("AEM"))

    // The id changes before the second lookup answers. Without the answer being keyed by its
    // question, "AEM" would still be readable here — for the wrong instance.
    rerender({ id: "i-2" })
    expect(result.current).toEqual({ provider: null, loading: true })

    second.resolve({ provider: "WEB-CRAWLER" })
    await waitFor(() => expect(result.current.provider).toBe("WEB-CRAWLER"))
    expect(result.current.loading).toBe(false)
  })

  it("stops loading on a failed lookup rather than spinning forever", async () => {
    const failing = deferred<{ provider: string }>()
    status.mockReturnValue(failing.promise)
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {})

    const { result } = renderHook(() => useConnectorProvider("i-broken"))

    failing.reject(new Error("status unavailable"))
    await waitFor(() => expect(result.current.loading).toBe(false))
    // A failure is an answer: the provider is unknown, and the caller must stop waiting.
    expect(result.current.provider).toBeNull()
    consoleError.mockRestore()
  })
})
