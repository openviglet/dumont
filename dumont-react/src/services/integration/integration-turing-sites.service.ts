import axios, { type AxiosInstance } from "axios";

export interface TuringSNSite {
  id: string;
  name: string;
  description: string;
}

export interface TuringSNSiteField {
  id: string;
  name: string;
  type: string;
}

/** Why Turing's sites could not be listed (D342): the connector answers 502 with this reason. */
export type SitesUnavailableReason = "unreachable" | "unauthorized" | "not_found" | "error";

/**
 * The reason a failed `query()` carries. Any failure the connector did not explain (the
 * connector itself unreachable, say) reads as `error`, never as an empty list.
 */
export function sitesUnavailableReason(error: unknown): SitesUnavailableReason {
  const reason = axios.isAxiosError(error)
    ? (error.response?.data as { reason?: string } | undefined)?.reason
    : undefined;
  return reason === "unreachable" || reason === "unauthorized" || reason === "not_found"
    ? reason
    : "error";
}

export class TurIntegrationTuringSitesService {
  private integrationId: string;
  private readonly axiosInstance: AxiosInstance;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  private get sitesUrl(): string {
    return `/v2/integration/${this.integrationId}/connector/turing-sn-sites`;
  }

  async query(): Promise<TuringSNSite[]> {
    const { data } = await this.axiosInstance.get<TuringSNSite[]>(this.sitesUrl);
    return data;
  }

  async fields(siteId: string): Promise<TuringSNSiteField[]> {
    const { data } = await this.axiosInstance.get<TuringSNSiteField[]>(
      `${this.sitesUrl}/${siteId}/fields`
    );
    return data;
  }
}
