import type { DumDiscoveryAPI } from "@/models/auth/discovery";
import type { DumRestInfo } from "@/models/auth/rest-info";
import axios from "axios";

/**
 * Session-based login + discovery + self-registration client (mirrors
 * TurAuthorizationService). Uses fetch for endpoints that must NOT be prefixed
 * by the axios interceptor with `/api`, letting the interceptor only touch
 * axios.get/post for REST calls elsewhere.
 */
export class DumAuthorizationService {
  async login(username: string, password: string): Promise<DumRestInfo> {
    const baseURL = axios.defaults.baseURL ?? "";
    const response = await fetch(`${baseURL}/api/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ username, password }),
    });
    if (!response.ok) {
      throw new Error("Invalid credentials");
    }
    return response.json() as Promise<DumRestInfo>;
  }

  async discovery(): Promise<DumDiscoveryAPI> {
    const response = await axios.create().get<DumDiscoveryAPI>("/api/v2/discovery");
    return response.data;
  }

  async register(data: {
    username: string;
    password: string;
    firstName: string;
    lastName: string;
    email: string;
  }): Promise<void> {
    const baseURL = axios.defaults.baseURL ?? "";
    const response = await fetch(`${baseURL}/api/v2/user/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || "Registration failed");
    }
  }
}
