import type { DumPrivilege } from "@/models/auth/role";
import axios from "axios";

export class DumPrivilegeService {
  async query(): Promise<DumPrivilege[]> {
    const response = await axios.get<DumPrivilege[]>("/v2/privilege");
    return response.data;
  }
}
