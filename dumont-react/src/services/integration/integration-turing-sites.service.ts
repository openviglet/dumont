import axios, { type AxiosInstance } from "axios";

export interface TuringSNSite {
  id: string;
  name: string;
  description: string;
}

export class TurIntegrationTuringSitesService {
  private integrationId: string;
  private readonly axiosInstance: AxiosInstance;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  async query(): Promise<TuringSNSite[]> {
    const { data } = await this.axiosInstance.get<TuringSNSite[]>(
      `/v2/integration/${this.integrationId}/connector/turing-sn-sites`
    );
    return data;
  }
}
