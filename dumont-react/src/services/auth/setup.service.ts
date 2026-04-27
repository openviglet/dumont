import axios from "axios";

export interface SetupStatus {
  required: boolean;
}

/**
 * First-access setup (mirrors TurSetupService). Uses fetch (not axios) but
 * still resolves against `axios.defaults.baseURL` ("/api") so the URL is
 * built consistently with the rest of the app.
 */
export class DumSetupService {
  async status(): Promise<SetupStatus> {
    const baseURL = axios.defaults.baseURL ?? "";
    const response = await fetch(`${baseURL}/setup`, {
      method: "GET",
      credentials: "include",
    });
    if (!response.ok) {
      throw new Error("Failed to check setup status");
    }
    return response.json() as Promise<SetupStatus>;
  }

  async setAdminPassword(password: string): Promise<void> {
    const baseURL = axios.defaults.baseURL ?? "";
    const response = await fetch(`${baseURL}/setup`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ password }),
    });
    if (!response.ok) {
      throw new Error("Failed to set admin password");
    }
  }
}
