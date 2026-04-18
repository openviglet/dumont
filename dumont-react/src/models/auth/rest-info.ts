export type DumRestInfo = {
  username: string;
  password?: string;
  firstName: string;
  lastName: string;
  admin: boolean;
  email: string;
  avatarUrl?: string;
  hasAvatar?: boolean;
  privileges?: string[];
};
