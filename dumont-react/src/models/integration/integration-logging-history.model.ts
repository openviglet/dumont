export type TurIntegrationLoggingEntry = {
  contentId?: string;
  url?: string;
  status?: string;
  resultStatus?: string;
  source?: string;
  environment?: string;
  locale?: string;
  transactionId?: string;
  checksum?: string;
  details?: string;
  date?: string;
  sites?: string[];
  [key: string]: unknown;
};

export type TurIntegrationLoggingPage = {
  content: TurIntegrationLoggingEntry[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
};

export type TurIntegrationLoggingStatus = {
  engine: string;
  enabled: boolean;
};
