/*
 * D90 — the test that holds the encoding.
 *
 * Twenty-seven files here held UTF-8 that had been decoded once as Windows-1252 and encoded
 * again. It was read as a comment problem — a mangled em-dash a reader scrolls past — right up
 * until the scan that found the whole class rather than the four spellings somebody had noticed:
 * four source forms were masking a password field with eight mangled bullets, and the coverage
 * page put a mangled arrow between two percentages. Both were in the JSX, on screen.
 *
 * So the guard is not a list of sequences. It asks the property — does this run of non-ASCII text
 * round-trip through Windows-1252 into valid UTF-8 — which is what the four-spelling reading
 * could not, and what makes the twenty-eighth spelling fail here rather than ship.
 *
 * Every literal below is built from code points. This file is scanned like every other, so an
 * example written as itself would be repaired by the next `--fix` and the case would assert that
 * clean text is clean.
 */
import { describe, expect, it } from "vitest"

import { findMojibake, repair, repairRun, scan, sources } from "../scripts/mojibake.mjs"

/** `—` as it arrives after a UTF-8 → Windows-1252 → UTF-8 round trip: E2 80 94, read as three. */
const MANGLED_EM_DASH = String.fromCodePoint(0x00e2, 0x20ac, 0x201d)
/** The same accident on `é`: C3 A9. */
const MANGLED_E_ACUTE = String.fromCodePoint(0x00c3, 0x00a9)

describe("D90: the tree is readable as it was written", () => {
  it("carries no double-encoded text", () => {
    const carrying = scan()

    expect(carrying.map((row) => row.file), [
      "A file was saved through a Windows-1252 round trip: an em-dash arrives as three",
      "  characters and an accented letter as two.",
      "`node scripts/mojibake.mjs` lists what each one holds, and `--fix` rewrites them.",
    ].join("\n")).toEqual([])
  })

  it("is not measuring an empty tree", () => {
    // The one wrong answer this reader must not give quietly: a walk that found nothing reports a
    // clean tree, which is indistinguishable from a repaired one.
    expect(sources().length).toBeGreaterThan(100)
  })
})

describe("D90: what the check counts as double-encoded", () => {
  it("puts back a run that round-trips", () => {
    expect(repairRun(MANGLED_EM_DASH)).toBe("—")
    expect(repairRun(MANGLED_E_ACUTE)).toBe("é")
  })

  it("refuses text that is merely not ASCII", () => {
    // Both are bytes Windows-1252 can spell and UTF-8 cannot read: a lone 0x97, and a two-byte
    // lead with nothing following it. Without this the check would rewrite every clean em-dash in
    // the tree into whatever the decoder made of one byte.
    expect(repairRun("—")).toBeNull()
    expect(repairRun("é")).toBeNull()
    expect(repairRun("⌘K")).toBeNull()
  })

  it("finds a run inside ordinary text, and leaves the rest alone", () => {
    const line = `the census ${MANGLED_EM_DASH} the instrument it is measured with`

    expect(findMojibake(line)).toHaveLength(1)
    expect(repair(line)).toBe("the census — the instrument it is measured with")
  })

  it("repairs once and then changes nothing", () => {
    // `--fix` runs over files it has already rewritten, so a second pass that kept finding
    // something would mean the repair had produced a new spelling of the defect.
    const once = repair(`a ${MANGLED_EM_DASH} b ${MANGLED_E_ACUTE}`)

    expect(repair(once)).toBe(once)
    expect(findMojibake(once)).toEqual([])
  })
})
