export type TurIntegrationAssetsSource = {
  id: string;
  name: string;
  description: string;
  sourceDir: string;
  prefixFromReplace: string;
  prefixToReplace: string;
  site: string;
  locale: string;
  contentType: string;
  chunk: number;
  typeInId: boolean;
  fileSizeField: string;
  fileExtensionField: string;
  encoding: string;
  showOutput: boolean;
  turSNSites: string[];
};
