import { UserMenu } from "@viglet/viglet-design-system";
import { useNavigate } from "react-router-dom";

import { ROUTES } from "@/app/routes.const";
import type { DumUser } from "@/models/auth/user";

interface HeaderUserMenuProps {
  user: DumUser;
}

/**
 * Thin wrapper over the design-system UserMenu that wires Dumont's
 * navigation and logout routes. Mirrors Turing's HeaderUserMenu so both
 * products share the same header interaction.
 */
export function HeaderUserMenu({ user }: Readonly<HeaderUserMenuProps>) {
  const navigate = useNavigate();

  return (
    <UserMenu
      user={{
        username: user.username,
        firstName: user.firstName,
        lastName: user.lastName,
        email: user.email,
        avatarUrl: user.avatarUrl,
      }}
      onAccount={() => navigate(ROUTES.USER_ACCOUNT)}
      onSignOut={() => {
        globalThis.location.href = ROUTES.LOGOUT;
      }}
    />
  );
}
