import { vigDesignSystemTranslations } from "@viglet/viglet-design-system/i18n";
import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";

import enAccount from "./locales/en/account.json";
import enBento from "./locales/en/bento.json";
import enCommon from "./locales/en/common.json";
import enDialog from "./locales/en/dialog.json";
import enForms from "./locales/en/forms.json";
import enIntegration from "./locales/en/integration.json";
import ptAccount from "./locales/pt/account.json";
import ptBento from "./locales/pt/bento.json";
import ptCommon from "./locales/pt/common.json";
import ptDialog from "./locales/pt/dialog.json";
import ptForms from "./locales/pt/forms.json";
import ptIntegration from "./locales/pt/integration.json";

const en = { ...enIntegration, ...enCommon, ...enForms, ...enDialog, ...enAccount, ...enBento };
const pt = { ...ptIntegration, ...ptCommon, ...ptForms, ...ptDialog, ...ptAccount, ...ptBento };

/**
 * D40 — the package underneath, this console on top.
 *
 * `registerVigTranslations` refuses to add any namespace the app already declares — it checks the
 * *top-level* key and skips the whole tree when it collides. Dumont declares `common`, `forms`,
 * `dialog`, `sidebar` and `theme`, so five of the package's nine bundles were being dropped
 * wholesale, in both languages. Nothing failed: every string in the layer goes through `t()` with
 * a `defaultValue`, so the chrome quietly rendered English inside a Portuguese console — which is
 * the failure mode that ships, because nobody notices their own default language.
 *
 * The bento chrome sits on the wrong side of that line. `BentoSaveBar` reads `common.cancel`,
 * `BentoActionsMenu` reads `forms.formActions.*`, `BentoStatusMarker` reads `common.active` and
 * `common.idle`; only the `bento.*` namespace itself was getting through, because Dumont happened
 * not to declare that one word.
 *
 * So the merge is inverted rather than patched: the package's translations are the *base*
 * resources, and Dumont's are deep-merged over them. A key Dumont defines still wins — that has
 * not changed, and `sidebar.indexing` is Dumont's own — but a key it does not define now falls
 * back to the package's translation instead of to an English default.
 *
 * This also means Dumont's `bento.json` must be merged, never spread into a base bundle that
 * replaces the package's `bento` tree: it carries only the handful of chrome strings this shell
 * owns (the skip link, the main-content label, the migration home), and the other twenty-odd
 * `bento.*` keys belong to the package.
 */
const base = vigDesignSystemTranslations;

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: base.en },
      pt: { translation: base.pt },
    },
    fallbackLng: "en",
    interpolation: {
      escapeValue: false,
    },
    detection: {
      order: ["localStorage", "navigator", "htmlTag", "cookie"],
      caches: ["localStorage"],
    },
  });

// Deep merge, overwriting: Dumont's wording wins wherever it has one.
i18n.addResourceBundle("en", "translation", en, true, true);
i18n.addResourceBundle("pt", "translation", pt, true, true);

export default i18n;
