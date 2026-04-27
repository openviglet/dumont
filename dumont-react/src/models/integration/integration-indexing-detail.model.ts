export type TurIntegrationIndexingDependency = {
  id: number;
  objectId: string;
};

export type TurIntegrationIndexingDetail = {
  id: number;
  objectId: string;
  provider: string;
  source: string;
  environment: string;
  transactionId: string;
  checksum: string;
  locale: string;
  created: string;
  modificationDate: string;
  status: string;
  standalone: boolean;
  sites: string[];
  dependencies: TurIntegrationIndexingDependency[];
};
