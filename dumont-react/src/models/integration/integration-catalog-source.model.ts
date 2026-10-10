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
  /** A declarative JSON API manifest (D92); when set it replaces `catalogUrl`. */
  jsonApiManifest?: string;
  /** JSON object of the values the manifest's `{secret:NAME}` placeholders resolve to. */
  jsonApiSecrets?: string;
  /** Turing Semantic Navigation site(s) this source publishes to. */
  turSNSites: string[];
};
