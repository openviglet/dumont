import type { TurIntegrationAssetsSource } from "@/models/integration/integration-assets-source.model";
import axios, { type AxiosInstance } from "axios";

export class TurIntegrationAssetsSourceService {
  private integrationId: string;
  private readonly axiosInstance: AxiosInstance;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  private get url(): string {
    return `/v2/integration/${this.integrationId}/assets/source`;
  }

  setIntegrationId(integrationId: string): this { this.integrationId = integrationId; return this; }

  async query(): Promise<TurIntegrationAssetsSource[]> {
    const { data } = await this.axiosInstance.get<TurIntegrationAssetsSource[]>(this.url); return data;
  }
  async get(id: string): Promise<TurIntegrationAssetsSource> {
    const { data } = await this.axiosInstance.get<TurIntegrationAssetsSource>(`${this.url}/${id}`); return data;
  }
  async getStructure(): Promise<TurIntegrationAssetsSource> {
    const { data } = await this.axiosInstance.get<TurIntegrationAssetsSource>(`${this.url}/structure`); return data;
  }
  async create(source: TurIntegrationAssetsSource): Promise<TurIntegrationAssetsSource> {
    const { data } = await this.axiosInstance.post<TurIntegrationAssetsSource>(this.url, source); return data;
  }
  async update(source: TurIntegrationAssetsSource): Promise<TurIntegrationAssetsSource> {
    const { data } = await this.axiosInstance.put<TurIntegrationAssetsSource>(`${this.url}/${source.id}`, source); return data;
  }
  async delete(source: TurIntegrationAssetsSource): Promise<boolean> {
    const { status } = await this.axiosInstance.delete(`${this.url}/${source.id}`); return status === 200;
  }
  async indexAll(source: TurIntegrationAssetsSource): Promise<void> {
    await this.axiosInstance.get(`${this.url}/${source.id}/indexAll`);
  }

  /** Every source of this kind as the zip the plugin's export writes (roadmap DU4). */
  async exportAll(): Promise<Blob> {
    const { data } = await this.axiosInstance.get<Blob>(`${this.url}/export`, {
      responseType: "blob",
    });
    return data;
  }

  /** Puts back a zip written by {@link exportAll}, here or on another instance. */
  async importAll(file: File): Promise<void> {
    const form = new FormData();
    form.append("file", file);
    await this.axiosInstance.post(`${this.url}/import`, form);
  }
}
