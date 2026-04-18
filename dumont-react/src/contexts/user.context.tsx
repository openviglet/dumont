import type { DumUser } from "@/models/auth/user";
import { DumUserService } from "@/services/auth/user.service";
import React from "react";

interface UserContextValue {
  user: DumUser;
  refreshUser: () => void;
}

const UserContext = React.createContext<UserContextValue | null>(null);

const dumUserService = new DumUserService();

export function UserProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = React.useState<DumUser>({} as DumUser);

  const refreshUser = React.useCallback(() => {
    dumUserService
      .get()
      .then((u) => setUser(u ?? ({} as DumUser)))
      .catch(() => setUser({} as DumUser));
  }, []);

  React.useEffect(() => {
    refreshUser();
  }, [refreshUser]);

  const value = React.useMemo(() => ({ user, refreshUser }), [user, refreshUser]);

  return <UserContext value={value}>{children}</UserContext>;
}

export function useCurrentUser(): UserContextValue {
  const ctx = React.useContext(UserContext);
  if (!ctx) throw new Error("useCurrentUser must be used within UserProvider");
  return ctx;
}
