import axios, { type AxiosInstance } from "axios";

/**
 * What the connector answers when asked to start work (D54).
 *
 * `DumConnectorApi` types all three of these endpoints as `ResponseEntity<Map<String, String>>`,
 * and every body it builds carries a single `status` key — `statusSent()` on acceptance,
 * `statusAlreadyRunning()` behind the 409 these callers already handle separately.
 *
 * An index signature rather than `{ status: string }` alone: the map is open on the Java side, so
 * a body that grows a second key stays assignable instead of forcing a change here first.
 */
export interface DumConnectorStatusResponse {
  status?: string;
  [key: string]: string | undefined;
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

  /**
   * Active connector plugin of this instance, carried as `provider` in the same
   * open string map the endpoints above answer with.
   */
  async status(): Promise<DumConnectorStatusResponse> {
    const { data } = await this.axiosInstance.get<DumConnectorStatusResponse>(
      `${this.connectorUrl}/status`,
    );
    return data;
  }

  async indexAll(source: string): Promise<DumConnectorStatusResponse> {
    const { data } = await this.axiosInstance.get<DumConnectorStatusResponse>(
      `${this.connectorUrl}/index/${source}/all`,
    );
    return data;
  }

  async reindexAll(source: string): Promise<DumConnectorStatusResponse> {
    const { data } = await this.axiosInstance.get<DumConnectorStatusResponse>(
      `${this.connectorUrl}/reindex/${source}/all`,
    );
    return data;
  }

  async auditSource(source: string): Promise<DumConnectorStatusResponse> {
    const { data } = await this.axiosInstance.get<DumConnectorStatusResponse>(
      `${this.connectorUrl}/audit/${source}`,
    );
    return data;
  }
}
