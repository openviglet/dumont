export type TurIntegrationCatalogSource = {
  id: string;
  name: string;
  description: string;
  /** URL serving this institution's already-normalized catalog JSON. */
  catalogUrl: string;
  /** Optional request header sent when fetching the catalog (e.g. an API key header). */
  authHeaderName: string;
  authHeaderValue: string;
  locale: string;
  /** Turing Semantic Navigation site(s) this source publishes to. */
  turSNSites: string[];
};
