import type { SourceReplacePlan } from "@/models/integration/source-replace-plan.model";
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

  /** Every source of this kind as the zip the plugin's export writes (roadmap DU4). */
  async exportAll(): Promise<Blob> {
    const { data } = await this.axiosInstance.get<Blob>(`${this.wcUrl}/export`, {
      responseType: "blob",
    });
    return data;
  }

  /** Puts back a zip written by {@link exportAll}, here or on another instance. */
  async importAll(file: File): Promise<void> {
    const form = new FormData();
    form.append("file", file);
    await this.axiosInstance.post(`${this.wcUrl}/import`, form);
  }

  /** What replacing every source with `file` would do, writing nothing (roadmap DU10). */
  async previewReplace(file: File): Promise<SourceReplacePlan> {
    const form = new FormData();
    form.append("file", file);
    const { data } = await this.axiosInstance.post<SourceReplacePlan>(`${this.wcUrl}/import`, form, {
      params: { mode: "replace", dryRun: true },
    });
    return data;
  }

  /** Makes `file` the whole set of sources of this type (roadmap DU9). */
  async replaceAll(file: File): Promise<void> {
    const form = new FormData();
    form.append("file", file);
    await this.axiosInstance.post(`${this.wcUrl}/import`, form, { params: { mode: "replace" } });
  }
}
