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
  /** What the properties say: an engine is named and switched on. */
  configured: boolean;
  /**
   * What a ping says (D75) — configured *and* answering. Gate a screen on this: a store that is
   * named and unreachable used to report itself on, so the history tab opened onto an empty list
   * rather than saying the log could not be read.
   */
  enabled: boolean;
  /**
   * Whether anything is actually writing the log (D84) — a Logback appender is attached and would
   * accept a row. `configured` and `enabled` both describe the *reader*, so a deployment that set
   * `dumont.logging.engine` but left `logging.config` at its default reports both true over a
   * history that is empty and always will be. Optional, because a connector older than 2026.3.7
   * does not send it, and absent is not the same as false.
   */
  writing?: boolean;
};
