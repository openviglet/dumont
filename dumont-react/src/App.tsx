import { Toaster } from "@viglet/viglet-design-system";
import { Navigate, Route, Routes } from "react-router-dom";
import ConsoleRootPage from "./app/console/console.root.page";
import HomePage from "./app/console/home/home.page";
import { ROUTES } from "./app/routes.const";
import { ThemeProvider } from "./components/theme-provider";
import { BreadcrumbProvider } from "./contexts/breadcrumb.context";
import { PluginProvider } from "./contexts/plugin.context";
import DumontRoutes from "./DumontRoutes";

/**
 * Standalone routing layout.
 *
 * Pages expect useParams() to return { id } (the integrationId) because in
 * Turing they are mounted under /admin/integration/instance/:id/*.
 * We replicate that structure here with a fixed "local" id so the pages work
 * unchanged. The axios interceptor in main.tsx strips this id from API calls.
 */
function App() {
  return (
    <ThemeProvider defaultTheme="system" storageKey="dumont-ui-theme">
      <Toaster richColors position="bottom-right" />
      <PluginProvider>
        <BreadcrumbProvider>
          <Routes>
            <Route path="/" element={<Navigate to={ROUTES.HOME} replace />} />
            <Route path={ROUTES.CONSOLE} element={<ConsoleRootPage />}>
              <Route path="home" element={<HomePage />} />
              {/* Mount dumont routes under integration/instance/:id/* so useParams().id works */}
              <Route path="integration/instance/:id/*" element={<DumontRoutes />} />
              {/* Redirect bare console path to home */}
              <Route index element={<Navigate to="home" replace />} />
            </Route>
          </Routes>
        </BreadcrumbProvider>
      </PluginProvider>
    </ThemeProvider>
  );
}

export default App;
