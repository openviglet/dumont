import axios from "axios";

export interface SetupStatus {
  required: boolean;
}

/**
 * First-access setup (mirrors TurSetupService). Uses fetch (not axios) so the
 * request goes through the `/api` prefix interceptor while preserving
 * credentials for the session cookie.
 */
export class DumSetupService {
  async status(): Promise<SetupStatus> {
    const baseURL = axios.defaults.baseURL ?? "";
    const response = await fetch(`${baseURL}/api/setup`, {
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
    const response = await fetch(`${baseURL}/api/setup`, {
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
