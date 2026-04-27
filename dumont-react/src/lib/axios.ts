import { reportBackendOffline, reportBackendOnline, toast } from "@viglet/viglet-design-system";
import axios, { type AxiosRequestConfig } from "axios";
import i18next from "i18next";

/**
 * Mirrors the Turing host axios setup. Imported by both the standalone entry
 * (main.tsx) and the Module Federation entry (DumontRoutes.tsx) so it applies
 * in both modes — Turing's vite config does not share `axios`, so Dumont's
 * axios instance is independent and must configure itself.
 *
 * Pattern matches `turing-app/src/main.tsx` + `turing-app/src/lib/axios.ts`:
 *  - baseURL = `${VITE_API_URL}/api` (services use `/v2/...` paths and `/api`
 *    is added implicitly).
 *  - Default `Content-Type: application/json` + `X-Requested-With` header.
 *  - CSRF token attached on mutating requests, with one retry on 403.
 *  - Backend offline/online reporting + global 422 (API key) toast + 401 redirect.
 */
// Fallback to empty string keeps baseURL relative ("/api") when VITE_API_URL
// is unset — required when Dumont is loaded as a Module Federation remote and
// the host doesn't propagate Dumont's .env to the bundle.
axios.defaults.baseURL = `${import.meta.env.VITE_API_URL ?? ""}/api`;
axios.defaults.withCredentials = true;
axios.defaults.xsrfCookieName = "XSRF-TOKEN";
axios.defaults.xsrfHeaderName = "X-XSRF-TOKEN";

const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_ENDPOINT = "/csrf";
let csrfToken: string | null = null;

const csrfClient = axios.create({ withCredentials: true });

function isMutatingMethod(method?: string): boolean {
  return ["post", "put", "delete", "patch"].includes(method?.toLowerCase() || "");
}

function isBackendUnreachable(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false;
  if (!error.response) return true;
  const status = error.response.status;
  return status === 502 || status === 503 || status === 504;
}

function readCsrfTokenFromHeaders(headers: unknown): string | null {
  if (!headers) return null;

  if (
    typeof headers === "object" &&
    headers !== null &&
    "get" in headers &&
    typeof (headers as { get: (name: string) => string | undefined }).get === "function"
  ) {
    const value = (headers as { get: (name: string) => string | undefined }).get(CSRF_HEADER);
    return value || null;
  }

  if (typeof headers === "object" && headers !== null) {
    const value = (headers as Record<string, string | undefined>)[CSRF_HEADER.toLowerCase()];
    return value || null;
  }

  return null;
}

function setRequestHeader(config: { headers?: unknown }, token: string): void {
  if (
    typeof config.headers === "object" &&
    config.headers !== null &&
    "set" in config.headers &&
    typeof (config.headers as { set: (name: string, value: string) => void }).set === "function"
  ) {
    (config.headers as { set: (name: string, value: string) => void }).set(CSRF_HEADER, token);
    return;
  }

  const headers = (config.headers as Record<string, string | undefined>) || {};
  headers[CSRF_HEADER] = token;
  config.headers = headers;
}

function clearCsrfToken(): void {
  csrfToken = null;
}

function readCsrfTokenFromCookie(): string | null {
  const regex = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/;
  const match = regex.exec(globalThis.document.cookie);
  return match ? decodeURIComponent(match[1]) : null;
}

async function ensureCsrfToken(): Promise<void> {
  if (csrfToken) return;

  csrfClient.defaults.baseURL = axios.defaults.baseURL;
  const response = await csrfClient.get(CSRF_ENDPOINT);
  const headerToken = readCsrfTokenFromHeaders(response.headers);
  const bodyToken =
    typeof response.data === "object" && response.data !== null
      ? (response.data as { token?: string }).token
      : undefined;
  const cookieToken = readCsrfTokenFromCookie();

  csrfToken = headerToken || bodyToken || cookieToken || null;
}

axios.interceptors.request.use(
  async (config) => {
    if (!(config.data instanceof FormData)) {
      config.headers["Content-Type"] = "application/json";
    }
    config.headers["X-Requested-With"] = "XMLHttpRequest";

    const requestUrl = config.url || "";
    if (requestUrl.includes(CSRF_ENDPOINT) || !isMutatingMethod(config.method)) {
      return config;
    }

    if (!csrfToken) {
      await ensureCsrfToken();
    }

    if (!csrfToken) {
      throw new Error("Unable to obtain CSRF token for mutating request.");
    }

    setRequestHeader(config, csrfToken);
    return config;
  },
  (error) => Promise.reject(error),
);

axios.interceptors.response.use(
  (response) => {
    reportBackendOnline();
    const tokenFromHeader = readCsrfTokenFromHeaders(response.headers);
    if (tokenFromHeader) {
      csrfToken = tokenFromHeader;
    }
    return response;
  },
  async (error) => {
    if (isBackendUnreachable(error)) {
      reportBackendOffline();
    } else if (axios.isAxiosError(error) && error.response) {
      reportBackendOnline();
      if (error.response.status === 422) {
        toast.error(i18next.t("common.apiKeyMismatch"));
      } else if (error.response.status === 401) {
        const { pathname, search } = globalThis.location;
        if (!pathname.startsWith("/login") && !pathname.startsWith("/setup")) {
          const returnUrl = pathname + search;
          globalThis.location.href = `/login?returnUrl=${encodeURIComponent(returnUrl)}`;
        }
      }
    }

    const tokenFromHeader = readCsrfTokenFromHeaders(error.response?.headers);
    if (tokenFromHeader) {
      csrfToken = tokenFromHeader;
    }

    const requestConfig = error.config as
      | (AxiosRequestConfig & { _csrfRetried?: boolean })
      | undefined;

    if (
      error.response?.status === 403 &&
      requestConfig &&
      !requestConfig._csrfRetried &&
      isMutatingMethod(requestConfig.method)
    ) {
      requestConfig._csrfRetried = true;
      clearCsrfToken();
      await ensureCsrfToken();

      if (!csrfToken) {
        throw error;
      }

      setRequestHeader(requestConfig, csrfToken);
      return axios.request(requestConfig);
    }

    throw error;
  },
);

export default axios;
