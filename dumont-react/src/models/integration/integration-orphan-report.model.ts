/** Where a report is in the review it exists to force. */
export type TurIntegrationOrphanReportStatus = "PENDING" | "APPLIED" | "DISCARDED";

/**
 * One audit pass's orphan set for a source on a site (D68), as the console reads it.
 *
 * `expiresAt` is null when expiry is switched off (`dumont.audit.index.orphanTtl` at or below
 * zero); `expired` is what the page renders, because the connector — not the browser's clock — is
 * what decides whether an apply is still allowed.
 */
export interface TurIntegrationOrphanReport {
  id: number;
  source: string;
  provider: string;
  site: string;
  discoveredAt: string;
  expiresAt: string | null;
  expired: boolean;
  status: TurIntegrationOrphanReportStatus;
  decidedAt: string | null;
  /** How many ids the pass found orphaned. */
  count: number;
  /** How many documents the site held when the pass ran — the count `count` is weighed against. */
  indexedCount: number;
  objectIds: string[];
}

/** What applying a report did. */
export interface TurIntegrationOrphanApplyResult {
  status: string;
  deindexed: number;
}
