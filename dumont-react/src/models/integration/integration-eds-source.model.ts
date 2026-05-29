/**
 * EDS source (Adobe Edge Delivery Services / aem.live).
 *
 * Backend entity is `com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource`
 * (see eds/eds-plugin). Field shapes match the JSON returned by
 * GET /api/v2/eds/source/{id}.
 */
export type TurIntegrationEdsIndex = {
  id?: string;
  path: string;
};

export type TurIntegrationEdsExcludePattern = {
  id?: string;
  pattern: string;
};

export type TurIntegrationEdsRemoveSelector = {
  id?: string;
  selector: string;
};

export type TurIntegrationEdsFacet = {
  id?: string;
  name: string;
  ordinal: number;
  allowedValues?: string;
};

export type TurIntegrationEdsFieldMapping = {
  id?: string;
  sourceField: string;
  targetField: string;
};

export type TurIntegrationEdsSource = {
  id: string;
  title: string;
  description: string;
  locale: string;
  baseUrl: string;
  sitemapFallback: string;
  bodySelector: string;
  userAgent: string;
  fetchTimeoutMs: number;
  requestsPerSecond: number;
  maxConcurrent: number;
  facetPathPattern: string;
  turSNSites: string[];
  indexes: TurIntegrationEdsIndex[];
  excludePatterns: TurIntegrationEdsExcludePattern[];
  removeSelectors: TurIntegrationEdsRemoveSelector[];
  facets: TurIntegrationEdsFacet[];
  fieldMappings: TurIntegrationEdsFieldMapping[];
};
