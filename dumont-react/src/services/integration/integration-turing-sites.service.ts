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
