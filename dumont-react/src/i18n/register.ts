import i18next from "i18next";

import enIntegration from "./locales/en/integration.json";
import ptIntegration from "./locales/pt/integration.json";
import enCommon from "./locales/en/common.json";
import ptCommon from "./locales/pt/common.json";
import enForms from "./locales/en/forms.json";
import ptForms from "./locales/pt/forms.json";
import enDialog from "./locales/en/dialog.json";
import ptDialog from "./locales/pt/dialog.json";
import enBento from "./locales/en/bento.json";
import ptBento from "./locales/pt/bento.json";

// Keys that belong to the host app and must not be overwritten
const HOST_KEYS = ["sidebar"];

function withoutHostKeys(resources: Record<string, unknown>) {
  const filtered = { ...resources };
  for (const key of HOST_KEYS) delete filtered[key];
  return filtered;
}

/*
 * All dumont translation bundles - add new ones here as pages are created.
 *
 * `bento` carries only the chrome strings this shell owns (D40). It is deep-merged like the rest,
 * which matters here: the host has already registered the package's full `bento` namespace, and a
 * shallow write would replace that tree with these three keys.
 */
const bundles = {
  en: withoutHostKeys({ ...enIntegration, ...enCommon, ...enForms, ...enDialog, ...enBento }),
  pt: withoutHostKeys({ ...ptIntegration, ...ptCommon, ...ptForms, ...ptDialog, ...ptBento }),
};

let registered = false;

export function registerDumontTranslations() {
  if (registered) return;
  for (const [lang, resources] of Object.entries(bundles)) {
    i18next.addResourceBundle(lang, "translation", resources, true, true);
  }
  registered = true;
}
