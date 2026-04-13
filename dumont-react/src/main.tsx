import i18n from "@/i18n";
import {
  BackendStatusProvider,
  ErrorBoundary,
  reportBackendOffline,
  reportBackendOnline,
  toast,
} from "@viglet/viglet-design-system";
import axios from "axios";
import React from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App.tsx";
import "./index.css";

/**
 * Standalone mode: rewrite service URLs so they hit the Dumont backend directly.
 *
 * Services build URLs like /v2/integration/{id}/aem/source (designed for Turing proxy).
 * The Dumont backend exposes them at /api/v2/aem/source (no integration segment).
 */
const INTEGRATION_PATH_RE = /^\/v2\/integration\/[^/]+\//;

axios.interceptors.request.use((config) => {
  if (config.url && INTEGRATION_PATH_RE.test(config.url)) {
    config.url = "/api" + config.url.replace(INTEGRATION_PATH_RE, "/v2/");
  } else if (config.url && config.url.startsWith("/") && !config.url.startsWith("/api")) {
    config.url = "/api" + config.url;
  }
  return config;
});

/**
 * Status codes that indicate the backend is effectively unreachable:
 * - no response at all (network/DNS/connection refused)
 * - 502 Bad Gateway / 503 Service Unavailable / 504 Gateway Timeout
 *   (these are what the Vite dev proxy / nginx / CDN returns when the upstream is down)
 */
function isBackendUnreachable(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false;
  if (!error.response) return true;
  const status = error.response.status;
  return status === 502 || status === 503 || status === 504;
}

axios.interceptors.response.use(
  (response) => {
    reportBackendOnline();
    return response;
  },
  (error) => {
    if (isBackendUnreachable(error)) {
      reportBackendOffline();
    } else if (axios.isAxiosError(error) && error.response) {
      // Any HTTP response from the backend (even 4xx) proves it is reachable.
      reportBackendOnline();
      if (error.response.status === 422) {
        toast.error(i18n.t("common.apiKeyMismatch"));
      }
    }
    return Promise.reject(error);
  }
);

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter basename="/dumont">
      <ErrorBoundary>
        <BackendStatusProvider healthEndpoint="/api/v2/ping">
          <App />
        </BackendStatusProvider>
      </ErrorBoundary>
    </BrowserRouter>
  </React.StrictMode>
);
