import type { DumUser } from "@/models/auth/user";
import axios from "axios";

/**
 * Current-user + self-service profile API (mirrors TurUserService in Turing).
 */
export class DumUserService {
  async get(): Promise<DumUser> {
    const response = await axios.get<DumUser>(`/v2/user/current`);
    return response.data;
  }

  async getByUsername(username: string): Promise<DumUser> {
    const response = await axios.get<DumUser>(`/v2/user/${username}`);
    return response.data;
  }

  async update(username: string, user: Partial<DumUser>): Promise<DumUser> {
    const response = await axios.put<DumUser>(`/v2/user/${username}`, user);
    return response.data;
  }

  async saveAvatarUrl(username: string, avatarUrl: string | null): Promise<void> {
    await axios.put(`/v2/user/${username}/avatar-url`, { avatarUrl });
  }
}
