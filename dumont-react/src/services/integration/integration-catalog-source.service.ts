import type { TurIntegrationCatalogSource } from "@/models/integration/integration-catalog-source.model";
import axios, { type AxiosInstance } from "axios";

export class TurIntegrationCatalogSourceService {
  private integrationId: string;
  private readonly axiosInstance: AxiosInstance;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  private get catalogUrl(): string {
    return `/v2/integration/${this.integrationId}/catalog/source`;
  }

  setIntegrationId(integrationId: string): this {
    this.integrationId = integrationId;
    return this;
  }

  async query(): Promise<TurIntegrationCatalogSource[]> {
    const { data } = await this.axiosInstance.get<TurIntegrationCatalogSource[]>(
      this.catalogUrl
    );
    return data;
  }

  async get(id: string): Promise<TurIntegrationCatalogSource> {
    const { data } = await this.axiosInstance.get<TurIntegrationCatalogSource>(
      `${this.catalogUrl}/${id}`
    );
    return data;
  }

  async getStructure(): Promise<TurIntegrationCatalogSource> {
    const { data } = await this.axiosInstance.get<TurIntegrationCatalogSource>(
      `${this.catalogUrl}/structure`
    );
    return data;
  }

  async create(
    source: TurIntegrationCatalogSource
  ): Promise<TurIntegrationCatalogSource> {
    const { data } = await this.axiosInstance.post<TurIntegrationCatalogSource>(
      this.catalogUrl,
      source
    );
    return data;
  }

  async update(
    source: TurIntegrationCatalogSource
  ): Promise<TurIntegrationCatalogSource> {
    const { data } = await this.axiosInstance.put<TurIntegrationCatalogSource>(
      `${this.catalogUrl}/${source.id}`,
      source
    );
    return data;
  }

  async delete(source: TurIntegrationCatalogSource): Promise<boolean> {
    const { status } = await this.axiosInstance.delete(
      `${this.catalogUrl}/${source.id}`
    );
    return status === 200;
  }

  async indexAll(source: TurIntegrationCatalogSource): Promise<void> {
    await this.axiosInstance.get(`${this.catalogUrl}/${source.id}/indexAll`);
  }
}
