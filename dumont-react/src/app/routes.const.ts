const consolePath: string = import.meta.env.VITE_CONSOLE_CONTEXT_PATH;

/**
 * Base path for an integration-instance surface.
 *
 * Dumont is mounted by the host (Turing) as a Module Federation remote under
 * `<hostPrefix>/integration/instance/:id/*`, and that host prefix differs
 * between the legacy console (`/admin`) and the new Bento shell (`/bento`).
 * Every internal link Dumont builds is `${ROUTES.INTEGRATION_INSTANCE}/${id}/…`,
 * so hard-coding the console path made those links jump OUT of the Bento shell
 * back to `/admin/...`. Deriving the prefix from the live URL keeps Dumont's
 * navigation inside whichever shell is hosting it. Falls back to the build-time
 * console path for standalone mode / before the first navigation.
 */
function integrationInstanceBase(): string {
  if (typeof window !== "undefined") {
    const match = /^(.*?)\/integration\/instance(?:\/|$)/.exec(window.location.pathname);
    if (match) return `${match[1]}/integration/instance`;
  }
  return `${consolePath}/integration/instance`;
}

export const ROUTES = {
  ROOT: "/",
  LOGIN: "/login",
  LOGOUT: "/logout",
  CONSOLE: consolePath,
  HOME: `${consolePath}/home`,
  SN_SEARCH: `/sn`,
  SN_ROOT: `${consolePath}/sn`,
  SN_INSTANCE: `${consolePath}/sn/instance`,
  SE_ROOT: `${consolePath}/se`,
  SE_INSTANCE: `${consolePath}/se/instance`,
  STORE_ROOT: `${consolePath}/store`,
  STORE_INSTANCE: `${consolePath}/store/instance`,
  TOKEN_ROOT: `${consolePath}/token`,
  TOKEN_INSTANCE: `${consolePath}/token/instance`,
  LLM_ROOT: `${consolePath}/llm`,
  LLM_INSTANCE: `${consolePath}/llm/instance`,
  INTEGRATION_ROOT: `${consolePath}/integration`,
  // Host-aware (console `/admin` vs Bento `/bento`) — see integrationInstanceBase().
  get INTEGRATION_INSTANCE() { return integrationInstanceBase(); },
  LOGGING_ROOT: `${consolePath}/logging`,
  LOGGING_INSTANCE: `${consolePath}/logging/instance`,
  DASHBOARD: `${consolePath}/dashboard`,
  GLOBAL_SETTINGS: `${consolePath}/global-settings`,
  EXCHANGE_ROOT: `${consolePath}/exchange`,
  EXCHANGE_IMPORT: `${consolePath}/exchange/import`,
  CHAT_ROOT: `${consolePath}/chat`,
  GRAPHQL_ROOT: `${consolePath}/graphql`,
  USER_ACCOUNT: `${consolePath}/user/account`,
  TOKEN_USAGE: `${consolePath}/token-usage`,
  MCP_ROOT: `${consolePath}/mcp`,
  MCP_INSTANCE: `${consolePath}/mcp/instance`,
  AI_AGENT_ROOT: `${consolePath}/ai-agent`,
  AI_AGENT_INSTANCE: `${consolePath}/ai-agent/instance`,
  ASSET_ROOT: `${consolePath}/asset`,
  EMBEDDING_MODEL_ROOT: `${consolePath}/embedding-model`,
  EMBEDDING_MODEL_INSTANCE: `${consolePath}/embedding-model/instance`,
  INTENT_ROOT: `${consolePath}/intent`,
  INTENT_INSTANCE: `${consolePath}/intent/instance`,
  ADMIN_ROOT: `${consolePath}/admin-settings`,
  ADMIN_USERS: `${consolePath}/admin-settings/users`,
  ADMIN_GROUPS: `${consolePath}/admin-settings/groups`,
  ADMIN_ROLES: `${consolePath}/admin-settings/roles`,
  ADMIN_SETTINGS: `${consolePath}/admin-settings/settings`,
  ADMIN_TOKENS: `${consolePath}/admin-settings/tokens`,
  ADMIN_SYSTEM_INFO: `${consolePath}/admin-settings/system-info`,
};
