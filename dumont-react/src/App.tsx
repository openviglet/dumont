import { Toaster } from "@viglet/viglet-design-system";
import { Navigate, Route, Routes } from "react-router-dom";
import AdminGroupPage from "./app/console/admin-settings/admin.group.page";
import AdminGroupsListPage from "./app/console/admin-settings/admin.groups.list.page";
import AdminRolePage from "./app/console/admin-settings/admin.role.page";
import AdminRolesListPage from "./app/console/admin-settings/admin.roles.list.page";
import AdminUserPage from "./app/console/admin-settings/admin.user.page";
import AdminUsersListPage from "./app/console/admin-settings/admin.users.list.page";
import ConsoleRootPage from "./app/console/console.root.page";
import HomePage from "./app/console/home/home.page";
import LoginPage from "./app/login/login.page";
import { ROUTES } from "./app/routes.const";
import SetupPage from "./app/setup/setup.page";
import { ThemeProvider } from "./components/theme-provider";
import { BreadcrumbProvider } from "./contexts/breadcrumb.context";
import { PluginProvider } from "./contexts/plugin.context";
import { UserProvider } from "./contexts/user.context";
import DumontRoutes from "./DumontRoutes";

/**
 * Standalone routing layout.
 *
 * Pages expect useParams() to return { id } (the integrationId) because in
 * Turing they are mounted under /admin/integration/instance/:id/*.
 * We replicate that structure here with a fixed "local" id so the pages work
 * unchanged. The axios interceptor in main.tsx strips this id from API calls.
 *
 * Authentication screens (login, setup) live outside the console so the
 * sidebar is not rendered before the user is authenticated. User/group/role
 * management mirrors Turing's layout under /admin-settings/*.
 */
function App() {
  return (
    <ThemeProvider defaultTheme="system" storageKey="dumont-ui-theme">
      <Toaster richColors position="bottom-right" />
      <PluginProvider>
        <UserProvider>
          <BreadcrumbProvider>
            <Routes>
              <Route path="/" element={<Navigate to={ROUTES.HOME} replace />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/setup" element={<SetupPage />} />
              <Route path={ROUTES.CONSOLE} element={<ConsoleRootPage />}>
                <Route path="home" element={<HomePage />} />
                <Route path="admin-settings">
                  <Route index element={<Navigate to="users" replace />} />
                  <Route path="users" element={<AdminUsersListPage />} />
                  <Route path="users/:username" element={<AdminUserPage />} />
                  <Route path="groups" element={<AdminGroupsListPage />} />
                  <Route path="groups/:id" element={<AdminGroupPage />} />
                  <Route path="roles" element={<AdminRolesListPage />} />
                  <Route path="roles/:id" element={<AdminRolePage />} />
                </Route>
                {/* Mount dumont routes under integration/instance/:id/* so useParams().id works */}
                <Route path="integration/instance/:id/*" element={<DumontRoutes />} />
                {/* Redirect bare console path to home */}
                <Route index element={<Navigate to="home" replace />} />
              </Route>
            </Routes>
          </BreadcrumbProvider>
        </UserProvider>
      </PluginProvider>
    </ThemeProvider>
  );
}

export default App;
