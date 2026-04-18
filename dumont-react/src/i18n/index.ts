import { registerVigTranslations } from "@viglet/viglet-design-system";
import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";

import enAccount from "./locales/en/account.json";
import enCommon from "./locales/en/common.json";
import enDialog from "./locales/en/dialog.json";
import enForms from "./locales/en/forms.json";
import enIntegration from "./locales/en/integration.json";
import ptAccount from "./locales/pt/account.json";
import ptCommon from "./locales/pt/common.json";
import ptDialog from "./locales/pt/dialog.json";
import ptForms from "./locales/pt/forms.json";
import ptIntegration from "./locales/pt/integration.json";

const en = { ...enIntegration, ...enCommon, ...enForms, ...enDialog, ...enAccount };
const pt = { ...ptIntegration, ...ptCommon, ...ptForms, ...ptDialog, ...ptAccount };

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
      pt: { translation: pt },
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

registerVigTranslations(i18n);

export default i18n;
