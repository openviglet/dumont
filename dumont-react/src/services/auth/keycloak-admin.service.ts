import type { DumKeycloakGroup } from "@/models/auth/keycloak-group";
import type { DumKeycloakUser } from "@/models/auth/keycloak-user";
import axios from "axios";

/**
 * Read-only Keycloak admin API client (mirrors TurKeycloakAdminService).
 * The backend delegates to the Keycloak Admin REST API using the current
 * user's OIDC access token.
 */
export class DumKeycloakAdminService {
  async listUsers(): Promise<DumKeycloakUser[]> {
    const response = await axios.get<DumKeycloakUser[]>("/v2/keycloak/users");
    return response.data;
  }

  async getUser(username: string): Promise<DumKeycloakUser> {
    const response = await axios.get<DumKeycloakUser>(`/v2/keycloak/users/${encodeURIComponent(username)}`);
    return response.data;
  }

  async listGroups(): Promise<DumKeycloakGroup[]> {
    const response = await axios.get<DumKeycloakGroup[]>("/v2/keycloak/groups");
    return response.data;
  }

  async getGroup(id: string): Promise<DumKeycloakGroup> {
    const response = await axios.get<DumKeycloakGroup>(`/v2/keycloak/groups/${encodeURIComponent(id)}`);
    return response.data;
  }
}
