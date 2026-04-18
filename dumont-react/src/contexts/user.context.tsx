import type { DumUser } from "@/models/auth/user";
import { DumUserService } from "@/services/auth/user.service";
import React from "react";

interface UserContextValue {
  user: DumUser;
  refreshUser: () => void;
}

const UserContext = React.createContext<UserContextValue | null>(null);

const dumUserService = new DumUserService();

function redirectToLoginIfNeeded() {
  const { pathname, search } = globalThis.location;
  if (pathname.startsWith("/login") || pathname.startsWith("/setup")) {
    return;
  }
  const returnUrl = pathname + search;
  globalThis.location.href = `/login?returnUrl=${encodeURIComponent(returnUrl)}`;
}

export function UserProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const [user, setUser] = React.useState<DumUser>({} as DumUser);

  const refreshUser = React.useCallback(() => {
    dumUserService
      .get()
      .then((u) => {
        if (u && u.username) {
          setUser(u);
        } else {
          redirectToLoginIfNeeded();
        }
      })
      .catch(() => redirectToLoginIfNeeded());
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
