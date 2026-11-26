import type { SourceReplacePlan } from "@/models/integration/source-replace-plan.model";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import axios, { type AxiosInstance } from "axios";

export class TurIntegrationAemSourceService {
  private integrationId: string;
  private readonly axiosInstance: AxiosInstance;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  private get aemUrl(): string {
    return `/v2/integration/${this.integrationId}/aem/source`;
  }

  private get connectorUrl(): string {
    return `/v2/integration/${this.integrationId}/connector`;
  }

  setIntegrationId(integrationId: string): this {
    this.integrationId = integrationId;
    return this;
  }

  async query(): Promise<TurIntegrationAemSource[]> {
    const { data } = await this.axiosInstance.get<TurIntegrationAemSource[]>(
      this.aemUrl
    );
    return data;
  }

  async get(id: string): Promise<TurIntegrationAemSource> {
    const { data } = await this.axiosInstance.get<TurIntegrationAemSource>(
      `${this.aemUrl}/${id}`
    );
    return data;
  }

  async getStructure(): Promise<TurIntegrationAemSource[]> {
    const { data } = await this.axiosInstance.get<TurIntegrationAemSource[]>(
      `${this.aemUrl}/structure`
    );
    return data;
  }

  async create(
    source: TurIntegrationAemSource
  ): Promise<TurIntegrationAemSource> {
    const { data } = await this.axiosInstance.post<TurIntegrationAemSource>(
      this.aemUrl,
      source
    );
    return data;
  }

  async update(
    source: TurIntegrationAemSource
  ): Promise<TurIntegrationAemSource> {
    const { data } = await this.axiosInstance.put<TurIntegrationAemSource>(
      `${this.aemUrl}/${source.id}`,
      source
    );
    return data;
  }

  async delete(source: TurIntegrationAemSource): Promise<boolean> {
    const { status } = await this.axiosInstance.delete(
      `${this.aemUrl}/${source.id}`
    );
    return status === 200;
  }

  async indexAll(source: TurIntegrationAemSource): Promise<void> {
    await this.axiosInstance.get(`${this.connectorUrl}/${source.id}/indexAll`);
  }

  async reindexAll(source: TurIntegrationAemSource): Promise<void> {
    await this.axiosInstance.get(
      `${this.connectorUrl}/${source.id}/reindexAll`
    );
  }

  async testConnection(payload: {
    endpoint: string;
    username: string;
    password: string;
  }): Promise<{ success: boolean; message: string }> {
    const { data } = await this.axiosInstance.post<{
      success: boolean;
      message: string;
    }>(`${this.aemUrl}/test-connection`, payload);
    return data;
  }

  async browse(payload: {
    endpoint: string;
    username: string;
    password: string;
    path: string;
  }): Promise<{
    success: boolean;
    children: { name: string; path: string; primaryType: string }[];
  }> {
    const { data } = await this.axiosInstance.post<{
      success: boolean;
      children: { name: string; path: string; primaryType: string }[];
    }>(`${this.aemUrl}/browse`, payload);
    return data;
  }

  async listExtractors(
    category: "attribute" | "content" | "url" | "delta",
    query?: string,
  ): Promise<{ fqcn: string; simpleName: string; packageName: string; category: string }[]> {
    const { data } = await this.axiosInstance.get<
      { fqcn: string; simpleName: string; packageName: string; category: string }[]
    >(`${this.aemUrl}/extractors`, {
      params: { category, ...(query ? { query } : {}) },
    });
    return data;
  }

  /** Every source of this kind as the zip the plugin's export writes (roadmap DU4). */
  async exportAll(): Promise<Blob> {
    const { data } = await this.axiosInstance.get<Blob>(`${this.aemUrl}/export`, {
      responseType: "blob",
    });
    return data;
  }

  /** Puts back a zip written by {@link exportAll}, here or on another instance. */
  async importAll(file: File): Promise<void> {
    const form = new FormData();
    form.append("file", file);
    await this.axiosInstance.post(`${this.aemUrl}/import`, form);
  }

  /** What replacing every source with `file` would do, writing nothing (roadmap DU10). */
  async previewReplace(file: File): Promise<SourceReplacePlan> {
    const form = new FormData();
    form.append("file", file);
    const { data } = await this.axiosInstance.post<SourceReplacePlan>(`${this.aemUrl}/import`, form, {
      params: { mode: "replace", dryRun: true },
    });
    return data;
  }

  /** Makes `file` the whole set of sources of this type (roadmap DU9). */
  async replaceAll(file: File): Promise<void> {
    const form = new FormData();
    form.append("file", file);
    await this.axiosInstance.post(`${this.aemUrl}/import`, form, { params: { mode: "replace" } });
  }
}
