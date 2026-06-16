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
