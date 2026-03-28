import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";

import enIntegration from "./locales/en/integration.json";
import ptIntegration from "./locales/pt/integration.json";
import enCommon from "./locales/en/common.json";
import ptCommon from "./locales/pt/common.json";

const en = { ...enIntegration, ...enCommon };
const pt = { ...ptIntegration, ...ptCommon };

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

export default i18n;
