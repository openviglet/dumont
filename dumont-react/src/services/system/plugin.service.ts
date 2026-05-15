import type { TurPlugin } from "@/models/system/plugin.model";
import axios, { type AxiosInstance } from "axios";

export class TurPluginService {
  private readonly axiosInstance: AxiosInstance;
  integrationId: string;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  private get url(): string {
    return `/v2/integration/${this.integrationId}/plugins`;
  }

  async list(): Promise<TurPlugin[]> {
    const { data } = await this.axiosInstance.get<TurPlugin[]>(this.url);
    return data;
  }

  async upload(file: File): Promise<TurPlugin> {
    const form = new FormData();
    form.append("file", file);
    const { data } = await this.axiosInstance.post<TurPlugin>(this.url, form);
    return data;
  }

  async start(id: string): Promise<TurPlugin> {
    const { data } = await this.axiosInstance.post<TurPlugin>(`${this.url}/${id}/start`);
    return data;
  }

  async stop(id: string): Promise<TurPlugin> {
    const { data } = await this.axiosInstance.post<TurPlugin>(`${this.url}/${id}/stop`);
    return data;
  }

  async disable(id: string): Promise<TurPlugin> {
    const { data } = await this.axiosInstance.post<TurPlugin>(`${this.url}/${id}/disable`);
    return data;
  }

  async enable(id: string): Promise<TurPlugin> {
    const { data } = await this.axiosInstance.post<TurPlugin>(`${this.url}/${id}/enable`);
    return data;
  }

  async delete(id: string): Promise<void> {
    await this.axiosInstance.delete(`${this.url}/${id}`);
  }

  async deleteFailed(filename: string): Promise<void> {
    await this.axiosInstance.delete(`${this.url}/file/${encodeURIComponent(filename)}`);
  }
}
