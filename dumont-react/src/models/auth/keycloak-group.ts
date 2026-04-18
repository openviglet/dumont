import type { DumKeycloakUser } from "./keycloak-user";

export type DumKeycloakGroup = {
  id: string;
  name: string;
  path?: string;
  realmRoles?: string[];
  subGroups?: DumKeycloakGroup[];
  members?: DumKeycloakUser[];
};
