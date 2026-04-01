import type { TurIntegrationWcSource } from "@/models/integration/integration-wc-source.model";
import axios, { type AxiosInstance } from "axios";

export class TurIntegrationWcSourceService {
  private integrationId: string;
  private readonly axiosInstance: AxiosInstance;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  private get wcUrl(): string {
    return `/v2/integration/${this.integrationId}/wc/source`;
  }

  setIntegrationId(integrationId: string): this {
    this.integrationId = integrationId;
    return this;
  }

  async query(): Promise<TurIntegrationWcSource[]> {
    const { data } = await this.axiosInstance.get<TurIntegrationWcSource[]>(
      this.wcUrl
    );
    return data;
  }

  async get(id: string): Promise<TurIntegrationWcSource> {
    const { data } = await this.axiosInstance.get<TurIntegrationWcSource>(
      `${this.wcUrl}/${id}`
    );
    return data;
  }

  async getStructure(): Promise<TurIntegrationWcSource> {
    const { data } = await this.axiosInstance.get<TurIntegrationWcSource>(
      `${this.wcUrl}/structure`
    );
    return data;
  }

  async create(
    source: TurIntegrationWcSource
  ): Promise<TurIntegrationWcSource> {
    const { data } = await this.axiosInstance.post<TurIntegrationWcSource>(
      this.wcUrl,
      source
    );
    return data;
  }

  async update(
    source: TurIntegrationWcSource
  ): Promise<TurIntegrationWcSource> {
    const { data } = await this.axiosInstance.put<TurIntegrationWcSource>(
      `${this.wcUrl}/${source.id}`,
      source
    );
    return data;
  }

  async delete(source: TurIntegrationWcSource): Promise<boolean> {
    const { status } = await this.axiosInstance.delete(
      `${this.wcUrl}/${source.id}`
    );
    return status === 200;
  }

  async crawl(source: TurIntegrationWcSource): Promise<void> {
    await this.axiosInstance.get(`${this.wcUrl}/${source.id}/crawl`);
  }
}
