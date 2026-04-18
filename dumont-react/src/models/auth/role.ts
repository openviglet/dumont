export type DumPrivilege = {
  id: string;
  name: string;
  description: string;
  category: string;
};

export type DumRole = {
  id: string;
  name: string;
  description: string;
  dumPrivileges: DumPrivilege[];
};
