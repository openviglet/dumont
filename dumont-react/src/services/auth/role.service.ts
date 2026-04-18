import type { DumRole } from "@/models/auth/role";
import axios from "axios";

export class DumRoleService {
  async query(): Promise<DumRole[]> {
    const response = await axios.get<DumRole[]>("/v2/role");
    return response.data;
  }

  async get(id: string): Promise<DumRole> {
    const response = await axios.get<DumRole>(`/v2/role/${id}`);
    return response.data;
  }

  async create(role: DumRole): Promise<DumRole> {
    const response = await axios.post<DumRole>("/v2/role", role);
    return response.data;
  }

  async update(id: string, role: DumRole): Promise<DumRole> {
    const response = await axios.put<DumRole>(`/v2/role/${id}`, role);
    return response.data;
  }

  async delete(id: string): Promise<boolean> {
    const response = await axios.delete(`/v2/role/${id}`);
    return response.status === 200;
  }
}
