import type { DumUser } from "@/models/auth/user";
import axios from "axios";

/**
 * Admin user management API (mirrors TurAdminUserService).
 */
export class DumAdminUserService {
  async query(): Promise<DumUser[]> {
    const response = await axios.get<DumUser[]>("/v2/user");
    return response.data;
  }

  async get(username: string): Promise<DumUser> {
    const response = await axios.get<DumUser>(`/v2/user/${username}`);
    return response.data;
  }

  async create(user: DumUser): Promise<DumUser> {
    const response = await axios.post<DumUser>("/v2/user", user);
    return response.data;
  }

  async update(username: string, user: DumUser): Promise<DumUser> {
    const response = await axios.put<DumUser>(`/v2/user/${username}`, user);
    return response.data;
  }

  async delete(username: string): Promise<boolean> {
    const response = await axios.delete(`/v2/user/${username}`);
    return response.status === 200;
  }
}
