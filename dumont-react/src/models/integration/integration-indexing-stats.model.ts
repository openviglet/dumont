export interface TurIntegrationIndexingStats {
    id: number;
    provider: string;
    source: string;
    operationType: "INDEX_ALL" | "REINDEX_ALL" | "DRY_SCAN";
    startTime: string;
    endTime: string;
    documentCount: number;
    documentsPerMinute: number;
    environment: string | null;
    locale: string | null;
    sites: string[];
    /** The run ended before reaching the whole source (a budget, a rate limit, an interruption). */
    partial?: boolean;
    /** Why the run ended as it did, as the source reported it (D96). */
    notes?: string[];
    /** What the run warned an operator of, partial or not (D130). */
    warnings?: string[];
    /** Whether the run raised a warning (D130). */
    warned?: boolean;
}
