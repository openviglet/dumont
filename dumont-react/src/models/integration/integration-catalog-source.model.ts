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
  /** An OAI-PMH 2.0 base URL (D315); when set the source harvests it instead of `catalogUrl`. */
  oaiPmhBaseUrl?: string;
  /** The OAI-PMH metadata format to harvest; blank means `oai_dc`. */
  oaiPmhMetadataPrefix?: string;
  /** The OAI-PMH set to harvest; blank means the whole repository. */
  oaiPmhSet?: string;
  /** Also read each record's PDF, from the repository's own host only. */
  oaiPmhFullText?: boolean;
  /** Turing Semantic Navigation site(s) this source publishes to. */
  turSNSites: string[];
};
