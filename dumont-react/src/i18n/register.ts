import i18next from "i18next";

import enIntegration from "./locales/en/integration.json";
import ptIntegration from "./locales/pt/integration.json";
import enCommon from "./locales/en/common.json";
import ptCommon from "./locales/pt/common.json";
import enForms from "./locales/en/forms.json";
import ptForms from "./locales/pt/forms.json";
import enDialog from "./locales/en/dialog.json";
import ptDialog from "./locales/pt/dialog.json";

// All dumont translation bundles - add new ones here as pages are created
const bundles = {
  en: { ...enIntegration, ...enCommon, ...enForms, ...enDialog },
  pt: { ...ptIntegration, ...ptCommon, ...ptForms, ...ptDialog },
};

let registered = false;

export function registerDumontTranslations() {
  if (registered) return;
  for (const [lang, resources] of Object.entries(bundles)) {
    i18next.addResourceBundle(lang, "translation", resources, true, true);
  }
  registered = true;
}
