import type { TurIntegrationEdsSource } from "@/models/integration/integration-eds-source.model";
import axios, { type AxiosInstance } from "axios";

/**
 * REST client for the EDS connector endpoints (eds-plugin):
 *
 *  - GET    /api/v2/eds/source           list
 *  - GET    /api/v2/eds/source/{id}      detail (eager-loads collections)
 *  - GET    /api/v2/eds/source/structure empty shape
 *  - POST   /api/v2/eds/source           create
 *  - PUT    /api/v2/eds/source/{id}      update
 *  - DELETE /api/v2/eds/source/{id}      delete
 *  - GET    /api/v2/eds/source/{id}/index    incremental crawl + index
 *  - GET    /api/v2/eds/source/{id}/reindex  clear state + reindex everything
 *  - GET    /api/v2/eds/source/{id}/dry-scan discover only (no fetch/index)
 *  - GET    /api/v2/eds/health              health roll-up
 */
export class TurIntegrationEdsSourceService {
  private integrationId: string;
  private readonly axiosInstance: AxiosInstance;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  private get edsUrl(): string {
    return `/v2/integration/${this.integrationId}/eds/source`;
  }

  setIntegrationId(integrationId: string): this {
    this.integrationId = integrationId;
    return this;
  }

  async query(): Promise<TurIntegrationEdsSource[]> {
    const { data } = await this.axiosInstance.get<TurIntegrationEdsSource[]>(this.edsUrl);
    return data;
  }

  async get(id: string): Promise<TurIntegrationEdsSource> {
    const { data } = await this.axiosInstance.get<TurIntegrationEdsSource>(
      `${this.edsUrl}/${id}`,
    );
    return data;
  }

  async getStructure(): Promise<TurIntegrationEdsSource> {
    const { data } = await this.axiosInstance.get<TurIntegrationEdsSource>(
      `${this.edsUrl}/structure`,
    );
    return data;
  }

  async create(source: TurIntegrationEdsSource): Promise<TurIntegrationEdsSource> {
    const { data } = await this.axiosInstance.post<TurIntegrationEdsSource>(this.edsUrl, source);
    return data;
  }

  async update(source: TurIntegrationEdsSource): Promise<TurIntegrationEdsSource> {
    const { data } = await this.axiosInstance.put<TurIntegrationEdsSource>(
      `${this.edsUrl}/${source.id}`,
      source,
    );
    return data;
  }

  async delete(source: TurIntegrationEdsSource): Promise<boolean> {
    const { status } = await this.axiosInstance.delete(`${this.edsUrl}/${source.id}`);
    return status === 200;
  }

  async index(source: TurIntegrationEdsSource): Promise<void> {
    await this.axiosInstance.get(`${this.edsUrl}/${source.id}/index`);
  }

  async reindex(source: TurIntegrationEdsSource): Promise<void> {
    await this.axiosInstance.get(`${this.edsUrl}/${source.id}/reindex`);
  }

  async dryScan(
    source: TurIntegrationEdsSource,
  ): Promise<{ discovered: number; paths: string[] }> {
    const { data } = await this.axiosInstance.get<{ discovered: number; paths: string[] }>(
      `${this.edsUrl}/${source.id}/dry-scan`,
    );
    return data;
  }
}
