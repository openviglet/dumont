import "@/i18n"; // Initialize i18n before lib/axios uses i18n.t in interceptors.
import "@/lib/axios"; // Configure axios baseURL, headers, CSRF, offline reporting.

import { BackendStatusProvider, ErrorBoundary } from "@viglet/viglet-design-system";
import axios from "axios";
import React from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App.tsx";
import "./index.css";

/**
 * Standalone-only: services build URLs like /v2/integration/{id}/aem/source
 * (designed for Turing's proxy). The Dumont backend exposes them at /v2/...
 * with no integration segment, so strip it here. The /api prefix is added
 * implicitly via axios.defaults.baseURL (set in @/lib/axios).
 */
const INTEGRATION_PATH_RE = /^\/v2\/integration\/[^/]+\//;

axios.interceptors.request.use((config) => {
  if (config.url && INTEGRATION_PATH_RE.test(config.url)) {
    config.url = config.url.replace(INTEGRATION_PATH_RE, "/v2/");
  }
  return config;
});

// Marker read by components that render differently when embedded as a remote.
// Set here in the standalone bootstrap; unset when Dumont is mounted via
// Module Federation inside another host (e.g. Turing).
(globalThis as unknown as { __DUMONT_STANDALONE__?: boolean }).__DUMONT_STANDALONE__ = true;

// `?loading=1` holds the index.html boot loader for design/regression testing
// (see the guard in index.html). Bail out before React mounts — the static
// markup inside `#root` stays visible, animations keep playing.
if (!(globalThis as Record<string, unknown>).__DUMONT_LOADING_TEST__) {
  createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
      <BrowserRouter basename="/">
        <ErrorBoundary>
          <BackendStatusProvider healthEndpoint="/v2/ping">
            <App />
          </BackendStatusProvider>
        </ErrorBoundary>
      </BrowserRouter>
    </React.StrictMode>
  );
}
