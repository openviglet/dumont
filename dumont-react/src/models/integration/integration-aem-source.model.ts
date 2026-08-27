import type { TurIntegrationAemAttributeSpec } from "./integration-aem-attribute-spec.model";
import type { TurIntegrationAemLocalePath } from "./integration-aem-locale-path.model";
import type { TurIntegrationAemPluginModel } from "./integration-aem-plugin-model.model";

/**
 * `SLING` reaches content through the author-tier servlets (`.infinity.json`,
 * `/bin/querybuilder.json`); `DELIVERY` through `.model.json` on the publish or
 * `delivery-pXXXX-eYYYY` host, which is what an AEM as a Cloud Service publish tier serves.
 */
export const TUR_INTEGRATION_AEM_FETCH_STRATEGIES = ["SLING", "DELIVERY"] as const;

export type TurIntegrationAemFetchStrategy =
  (typeof TUR_INTEGRATION_AEM_FETCH_STRATEGIES)[number];

export type TurIntegrationAemSource = {
  id: string;
  name: string;
  endpoint: string;
  username: string;
  password: string;
  rootPath: string;
  contentType: string;
  subType: string;
  oncePattern: string;
  defaultLocale: string;
  localeClass: string;
  deltaClass: string;
  dependencyClass: string;
  indexConditionClass: string;
  author: boolean;
  publish: boolean;
  authorSNSite: string;
  publishSNSite: string;
  authorURLPrefix: string;
  publishURLPrefix: string;
  /** How content is fetched: the author-tier Sling servlets, or the publish delivery APIs. */
  fetchStrategy: TurIntegrationAemFetchStrategy;
  deliveryURLPrefix: string;
  localePaths: TurIntegrationAemLocalePath[];
  attributeSpecifications: TurIntegrationAemAttributeSpec[];
  models: TurIntegrationAemPluginModel[];
};
