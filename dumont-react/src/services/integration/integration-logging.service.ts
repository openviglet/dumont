import type {
  TurIntegrationLoggingPage,
  TurIntegrationLoggingStatus,
} from "@/models/integration/integration-logging-history.model";
import axios, { type AxiosInstance } from "axios";

export class TurIntegrationLoggingService {
  private integrationId: string;
  private readonly axiosInstance: AxiosInstance;
  private endpointPrefix: string;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
    this.endpointPrefix = `/v2/integration/${this.integrationId}/connector/logging`;
  }

  async status(): Promise<TurIntegrationLoggingStatus> {
    const { data } = await this.axiosInstance.get<TurIntegrationLoggingStatus>(
      `${this.endpointPrefix}/status`,
    );
    return data;
  }

  async indexingHistory(params: {
    contentId: string;
    page?: number;
    pageSize?: number;
    sort?: "asc" | "desc";
  }): Promise<TurIntegrationLoggingPage> {
    const { data } = await this.axiosInstance.get<TurIntegrationLoggingPage>(
      `${this.endpointPrefix}/indexing`,
      {
        params: {
          contentId: params.contentId,
          page: params.page ?? 0,
          pageSize: params.pageSize ?? 100,
          sort: params.sort ?? "desc",
        },
      },
    );
    return data;
  }
}
