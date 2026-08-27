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
  /**
   * Rows the engine could not read and skipped (D74) — always present, so `0` means "nothing was
   * skipped" rather than "this response does not say". Non-zero on a Redis log still holding rows
   * written before 2026.3.6.
   */
  unreadable: number;
};

export type TurIntegrationLoggingStatus = {
  engine: string;
  enabled: boolean;
};
