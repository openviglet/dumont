import type { DumRole } from "./role";
import type { DumUser } from "./user";

export type DumGroup = {
  id: string;
  name: string;
  description: string;
  dumUsers?: DumUser[];
  dumRoles?: DumRole[];
};
