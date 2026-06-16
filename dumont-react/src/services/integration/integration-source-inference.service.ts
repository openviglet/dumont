import type {
  DumInferIndexResult,
  DumSourceDraft,
} from "@/models/integration/integration-source-inference.model";
import axios, { type AxiosInstance } from "axios";

/**
 * Calls the D13 source-inference endpoints. The integration segment is stripped
 * by the standalone axios interceptor, so these resolve to
 * `/api/v2/connector/source/infer` and `/api/v2/connector/source/infer/index`.
 */
export class TurIntegrationSourceInferenceService {
  private integrationId: string;
  private readonly axiosInstance: AxiosInstance;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  private get baseUrl(): string {
    return `/v2/integration/${this.integrationId}/connector/source/infer`;
  }

  /** Classify a URL and propose an editable draft. */
  async infer(url: string): Promise<DumSourceDraft> {
    const { data } = await this.axiosInstance.post<DumSourceDraft>(this.baseUrl, { url });
    return data;
  }

  /** Index a human-confirmed draft end-to-end. */
  async index(request: {
    url: string;
    sourceName: string;
    siteNames: string[];
    locale?: string;
    draft: DumSourceDraft;
  }): Promise<DumInferIndexResult> {
    const { data } = await this.axiosInstance.post<DumInferIndexResult>(
      `${this.baseUrl}/index`,
      request
    );
    return data;
  }
}
