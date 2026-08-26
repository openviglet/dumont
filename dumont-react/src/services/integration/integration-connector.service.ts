import axios, { type AxiosInstance } from "axios";

export interface TurIntegrationConnectorStatus {
  status: string;
  provider: string;
}

export class TurIntegrationConnectorService {
  private readonly axiosInstance: AxiosInstance;
  integrationId: string;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  private get connectorUrl(): string {
    return `/v2/integration/${this.integrationId}/connector`;
  }

  async status(): Promise<TurIntegrationConnectorStatus> {
    const { data } = await this.axiosInstance.get<TurIntegrationConnectorStatus>(
      `${this.connectorUrl}/status`,
    );
    return data;
  }

  async indexAll(source: string): Promise<any> {
    const { data } = await this.axiosInstance.get<any>(
      `${this.connectorUrl}/index/${source}/all`,
    );
    return data;
  }

  async reindexAll(source: string): Promise<any> {
    const { data } = await this.axiosInstance.get<any>(
      `${this.connectorUrl}/reindex/${source}/all`,
    );
    return data;
  }

  async auditSource(source: string): Promise<any> {
    const { data } = await this.axiosInstance.get<any>(
      `${this.connectorUrl}/audit/${source}`,
    );
    return data;
  }
}
