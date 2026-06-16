import type {
    TurIntegrationCoverageDrift,
    TurIntegrationCoverageSnapshot,
} from "@/models/integration/integration-coverage.model";
import axios, { type AxiosInstance } from "axios";

/** Reads per-source field-coverage history and drift (roadmap D15). */
export class TurIntegrationCoverageService {
    private readonly axiosInstance: AxiosInstance;
    private readonly endpointPrefix: string;

    constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
        this.axiosInstance = axiosInstance;
        this.endpointPrefix = `/v2/integration/${integrationId}/connector/monitoring/indexing`;
    }

    async getHistory(source: string): Promise<TurIntegrationCoverageSnapshot[]> {
        const { data } = await this.axiosInstance.get<TurIntegrationCoverageSnapshot[]>(
            `${this.endpointPrefix}/coverage/${encodeURIComponent(source)}`
        );
        return data;
    }

    async getDrift(source: string): Promise<TurIntegrationCoverageDrift> {
        const { data } = await this.axiosInstance.get<TurIntegrationCoverageDrift>(
            `${this.endpointPrefix}/coverage/${encodeURIComponent(source)}/drift`
        );
        return data;
    }

    /** The endpoint the shared {@code AiSummaryPanel} fetches for the LLM health summary. */
    summaryEndpoint(source: string): string {
        return `${this.endpointPrefix}/coverage/${encodeURIComponent(source)}/summary`;
    }

    async getCountBySource(): Promise<Record<string, number>> {
        const { data } = await this.axiosInstance.get<Record<string, number>>(
            `${this.endpointPrefix}/stats/count`
        );
        return data;
    }
}
