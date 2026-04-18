import type { DumGroup } from "./group";

export type DumUser = {
  username: string;
  firstName: string;
  lastName: string;
  admin: boolean;
  email: string;
  password?: string;
  hasAvatar?: boolean;
  avatarUrl?: string;
  realm?: string;
  dumGroups?: DumGroup[];
  privileges?: string[];
};
