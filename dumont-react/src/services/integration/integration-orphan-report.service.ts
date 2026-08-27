import type {
  TurIntegrationOrphanApplyResult,
  TurIntegrationOrphanReport,
} from "@/models/integration/integration-orphan-report.model";
import axios, { type AxiosInstance } from "axios";

/**
 * The orphan reports the index audit leaves for review (D68), and the two decisions that close one.
 *
 * `apply` and `discard` are separate calls rather than one with a flag: they are different acts,
 * and only one of them deletes anything.
 */
export class TurIntegrationOrphanReportService {
  private readonly integrationId: string;
  private readonly axiosInstance: AxiosInstance;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  private get orphansUrl(): string {
    return `/v2/integration/${this.integrationId}/connector/orphans`;
  }

  async query(source?: string): Promise<TurIntegrationOrphanReport[]> {
    const { data } = await this.axiosInstance.get<TurIntegrationOrphanReport[]>(this.orphansUrl, {
      params: source ? { source } : undefined,
    });
    return data;
  }

  async apply(reportId: number): Promise<TurIntegrationOrphanApplyResult> {
    const { data } = await this.axiosInstance.post<TurIntegrationOrphanApplyResult>(
      `${this.orphansUrl}/${reportId}/apply`,
    );
    return data;
  }

  async discard(reportId: number): Promise<TurIntegrationOrphanApplyResult> {
    const { data } = await this.axiosInstance.post<TurIntegrationOrphanApplyResult>(
      `${this.orphansUrl}/${reportId}/discard`,
    );
    return data;
  }
}
