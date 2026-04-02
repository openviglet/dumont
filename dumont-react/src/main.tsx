import i18n from "@/i18n";
import axios from "axios";
import { toast } from "sonner";
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

axios.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 422) {
      toast.error(i18n.t("common.apiKeyMismatch"));
    }
    return Promise.reject(error);
  }
);

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter basename="/dumont">
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
