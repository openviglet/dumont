/** Field-coverage & drift monitoring (roadmap D15). */

export interface TurIntegrationFieldCoverage {
    field: string;
    type: string;
    mandatory: boolean;
    present: number;
    total: number;
    percent: number;
}

export interface TurIntegrationCoverageSnapshot {
    source: string;
    capturedAt: number;
    documentCount: number;
    fields: TurIntegrationFieldCoverage[];
}

export interface TurIntegrationFieldDrift {
    field: string;
    previousPercent: number;
    currentPercent: number;
    delta: number;
    drift: boolean;
}

export interface TurIntegrationCoverageDrift {
    source: string;
    hasDrift: boolean;
    dropThreshold: number;
    previousRunAt: number;
    currentRunAt: number;
    fields: TurIntegrationFieldDrift[];
}

/** Field-level provenance & last-seen (roadmap D16). */

export interface TurIntegrationFieldLastSeen {
    field: string;
    strategy: string | null;
    selector: string | null;
    confidence: number | null;
    presentInLatest: boolean;
    /** 0 when present in the latest run, N>0 when last seen N runs ago, -1 when never seen. */
    lastSeenRunsAgo: number;
    lastSeenAt: number;
}

export interface TurIntegrationProvenanceLastSeen {
    source: string;
    fields: TurIntegrationFieldLastSeen[];
}
