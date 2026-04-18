import type { DumGroup } from "@/models/auth/group";
import axios from "axios";

export class DumGroupService {
  async query(): Promise<DumGroup[]> {
    const response = await axios.get<DumGroup[]>("/v2/group");
    return response.data;
  }

  async get(id: string): Promise<DumGroup> {
    const response = await axios.get<DumGroup>(`/v2/group/${id}`);
    return response.data;
  }

  async create(group: DumGroup): Promise<DumGroup> {
    const response = await axios.post<DumGroup>("/v2/group", group);
    return response.data;
  }

  async update(id: string, group: DumGroup): Promise<DumGroup> {
    const response = await axios.put<DumGroup>(`/v2/group/${id}`, group);
    return response.data;
  }

  async delete(id: string): Promise<boolean> {
    const response = await axios.delete(`/v2/group/${id}`);
    return response.status === 200;
  }
}
