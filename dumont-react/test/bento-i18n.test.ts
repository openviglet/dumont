/*
 * D40 — no bento chrome string renders in the wrong language.
 *
 * Every string in the package's bento layer goes through `t()` with a `defaultValue`, which is
 * what makes an untranslated console render English rather than a raw key. It is also what makes
 * this failure silent: a missing translation looks exactly like a deliberate English label, and
 * the person least likely to notice is the one whose own language is the default.
 *
 * So the assertion cannot be "the key resolves" — a `defaultValue` always resolves. It has to be
 * "the Portuguese differs from the English", which is the only observable difference between a
 * translation that arrived and one that fell back.
 *
 * The keys below are the ones Dumont's own shell renders, plus the ones the components it mounts
 * render internally. They are listed rather than discovered because a discovered list would grow
 * silently: a new bento surface that adds a string must be added here, and that is the point.
 */
import { describe, expect, it } from "vitest"

import i18n from "@/i18n"

/** Languages this console ships. A new one belongs here and in `locales/`. */
const LANGUAGES = ["en", "pt"] as const

/**
 * Chrome the *package* owns and Dumont mounts: the rail's landmark, the palette, the save-bar
 * markers, the actions menu, the status pills, the user menu.
 */
const PACKAGE_CHROME = [
  "bento.nav.label",
  "bento.palette.title",
  "bento.palette.description",
  "bento.palette.placeholder",
  "bento.palette.search",
  "bento.palette.open",
  "bento.palette.noResults",
  "bento.palette.opensConsole",
  "bento.saveBar.unsaved",
  "bento.saveBar.titleRequired",
  "bento.layout.customize",
  "bento.layout.save",
  "bento.layout.reset",
  "common.cancel",
  "common.active",
  "common.idle",
  "forms.formActions.cancel",
  "forms.formActions.saveChanges",
  "forms.formActions.delete",
  "nav.account",
  "nav.logOut",
]

/** Chrome *this* shell owns — the skip link, the main landmark, the home and the section hubs. */
const DUMONT_CHROME = [
  "bento.skipToContent",
  "bento.mainContent",
  "bento.home.subtitle",
  "bento.home.emptyTitle",
  "bento.home.emptyDescription",
  "bento.area.emptyTitle",
  "bento.area.emptyDescription",
  "bento.sections.sources.description",
  "bento.sections.integration.description",
  "home.title",
  "sidebar.sources",
  "sidebar.brandName",
  "integration.title",
]

const ALL_CHROME = [...PACKAGE_CHROME, ...DUMONT_CHROME]

/**
 * Keys whose Portuguese is *correctly* identical to their English.
 *
 * The differs-from-English rule has one honest exception: a proper noun is the same word in every
 * language, and "translating" the product's name would be the actual bug. Listed explicitly
 * rather than dropped from `ALL_CHROME`, so the exemption is visible and stays small — an
 * unexplained absence is how a gate loses its teeth one key at a time.
 */
const SAME_IN_EVERY_LANGUAGE = new Set(["sidebar.brandName"])

describe("D40: every bento chrome string is translated in every language shipped", () => {
  it.each(LANGUAGES)("resolves all of them in %s", (lang) => {
    const t = i18n.getFixedT(lang)
    const missing = ALL_CHROME.filter((key) => {
      const value = t(key)
      // A key that resolves to itself never reached a bundle at all.
      return typeof value !== "string" || value.length === 0 || value === key
    })
    expect(missing, `unresolved bento chrome keys in ${lang}`).toEqual([])
  })

  it("renders Portuguese, not an English default, for every one of them", () => {
    /*
     * The assertion that actually catches the bug. Before D40 the package's `common`, `forms` and
     * `dialog` bundles were skipped whenever Dumont declared the same top-level key, so
     * `common.cancel` and `forms.formActions.*` came back as their English `defaultValue` under
     * `pt` — a Portuguese console with English chrome, and nothing failing anywhere.
     */
    const en = i18n.getFixedT("en")
    const pt = i18n.getFixedT("pt")
    const stillEnglish = ALL_CHROME.filter(
      (key) => !SAME_IN_EVERY_LANGUAGE.has(key) && en(key) === pt(key),
    )
    expect(stillEnglish, "these render their English text under pt").toEqual([])
  })
})

describe("D40: adding Dumont's own bento strings does not displace the package's", () => {
  it("keeps the package's bento namespace alongside this shell's additions", () => {
    /*
     * The trap this task had to avoid. Dumont's translations are merged over the package's, and
     * `bento.json` declares the same top-level `bento` word the package does. A shallow write —
     * or spreading it into a base bundle that replaces the tree — would leave the console with
     * exactly the three keys below and none of the twenty the package ships.
     */
    for (const lang of LANGUAGES) {
      const bundle = i18n.getResourceBundle(lang, "translation") as Record<string, unknown>
      const bento = bundle.bento as Record<string, unknown>

      // Dumont's own.
      expect(bento.skipToContent).toBeTypeOf("string")
      // The package's, which must have survived the merge.
      expect(bento.palette).toBeTypeOf("object")
      expect(bento.saveBar).toBeTypeOf("object")
      expect(bento.shortcuts).toBeTypeOf("object")
    }
  })

  it("still lets Dumont's own wording win where it has one", () => {
    // The package ships `sidebar.indexing` too. Dumont's must be what renders — the inverted
    // merge changes which side fills the gaps, not which side wins a conflict.
    expect(i18n.getFixedT("en")("sidebar.brandTagline")).toBe("Data Exchange Platform")
    expect(i18n.getFixedT("pt")("sidebar.brandTagline")).toBe("Plataforma de Troca de Dados")
  })
})
