import { Navigate, Route, Routes } from "react-router-dom";
import { ThemeProvider } from "./components/theme-provider";
import { BreadcrumbProvider } from "./contexts/breadcrumb.context";
import { ROUTES } from "./app/routes.const";
import ConsoleRootPage from "./app/console/console.root.page";
import HomePage from "./app/console/home/home.page";
import DumontRoutes from "./DumontRoutes";

function App() {
  return (
    <ThemeProvider defaultTheme="system" storageKey="dumont-ui-theme">
      <BreadcrumbProvider>
        <Routes>
          <Route path="/" element={<Navigate to={ROUTES.HOME} replace />} />
          <Route path={ROUTES.CONSOLE} element={<ConsoleRootPage />}>
            <Route path="home" element={<HomePage />} />
            <Route path="*" element={<DumontRoutes />} />
          </Route>
        </Routes>
      </BreadcrumbProvider>
    </ThemeProvider>
  );
}

export default App;
