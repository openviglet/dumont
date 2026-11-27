import type { SourceImportMode, SourceReplacePlan } from "@/models/integration/source-replace-plan.model";
import type { TurIntegrationDbSource } from "@/models/integration/integration-db-source.model";
import axios, { type AxiosInstance } from "axios";

export class TurIntegrationDbSourceService {
  private integrationId: string;
  private readonly axiosInstance: AxiosInstance;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  private get dbUrl(): string {
    return `/v2/integration/${this.integrationId}/db/source`;
  }

  private get connectorUrl(): string {
    return `/v2/integration/${this.integrationId}/connector`;
  }

  setIntegrationId(integrationId: string): this {
    this.integrationId = integrationId;
    return this;
  }

  async query(): Promise<TurIntegrationDbSource[]> {
    const { data } = await this.axiosInstance.get<TurIntegrationDbSource[]>(
      this.dbUrl
    );
    return data;
  }

  async get(id: string): Promise<TurIntegrationDbSource> {
    const { data } = await this.axiosInstance.get<TurIntegrationDbSource>(
      `${this.dbUrl}/${id}`
    );
    return data;
  }

  async getStructure(): Promise<TurIntegrationDbSource> {
    const { data } = await this.axiosInstance.get<TurIntegrationDbSource>(
      `${this.dbUrl}/structure`
    );
    return data;
  }

  async create(
    source: TurIntegrationDbSource
  ): Promise<TurIntegrationDbSource> {
    const { data } = await this.axiosInstance.post<TurIntegrationDbSource>(
      this.dbUrl,
      source
    );
    return data;
  }

  async update(
    source: TurIntegrationDbSource
  ): Promise<TurIntegrationDbSource> {
    const { data } = await this.axiosInstance.put<TurIntegrationDbSource>(
      `${this.dbUrl}/${source.id}`,
      source
    );
    return data;
  }

  async delete(source: TurIntegrationDbSource): Promise<boolean> {
    const { status } = await this.axiosInstance.delete(
      `${this.dbUrl}/${source.id}`
    );
    return status === 200;
  }

  async indexAll(source: TurIntegrationDbSource): Promise<void> {
    await this.axiosInstance.get(`${this.connectorUrl}/${source.name}/indexAll`);
  }

  async reindexAll(source: TurIntegrationDbSource): Promise<void> {
    await this.axiosInstance.get(
      `${this.connectorUrl}/${source.name}/reindexAll`
    );
  }

  /** Every source of this kind as the zip the plugin's export writes (roadmap DU4). */
  async exportAll(): Promise<Blob> {
    const { data } = await this.axiosInstance.get<Blob>(`${this.dbUrl}/export`, {
      responseType: "blob",
    });
    return data;
  }

  /** What importing `file` in `mode` would do, writing nothing (roadmap DU10, DU14). */
  async previewImport(file: File, mode: SourceImportMode): Promise<SourceReplacePlan> {
    const form = new FormData();
    form.append("file", file);
    const { data } = await this.axiosInstance.post<SourceReplacePlan>(`${this.dbUrl}/import`, form, {
      params: { mode, dryRun: true },
    });
    return data;
  }

  /**
   * Imports `file`, a zip or JSON written by {@link exportAll}: `merge` updates the sources it names
   * and keeps the others, `replace` makes it the whole set (roadmap DU9, DU14).
   */
  async importFile(file: File, mode: SourceImportMode): Promise<void> {
    const form = new FormData();
    form.append("file", file);
    await this.axiosInstance.post(`${this.dbUrl}/import`, form, { params: { mode } });
  }
}
