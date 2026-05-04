import type { TurIntegrationQueueStatus } from "@/models/integration/integration-queue-status.model";
import axios, { type AxiosInstance } from "axios";

export class TurIntegrationQueueService {
  private readonly axiosInstance: AxiosInstance;
  integrationId: string;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  private get queueUrl(): string {
    return `/v2/integration/${this.integrationId}/connector/queue`;
  }

  async getStatus(): Promise<TurIntegrationQueueStatus> {
    const { data } = await this.axiosInstance.get<TurIntegrationQueueStatus>(
      `${this.queueUrl}/status`,
    );
    return data;
  }
}
