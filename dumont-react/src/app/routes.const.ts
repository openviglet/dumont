const consolePath: string = import.meta.env.VITE_CONSOLE_CONTEXT_PATH;

/**
 * Base path for the bento shell — the second era's tree, mounted beside the console one (D37).
 *
 * Both trees serve. A surface migrates by being added under this prefix while its console route
 * keeps working, which is what makes each of the 39 pages reversible one at a time; nothing here
 * redirects, and which tree a person reaches is not this file's decision.
 *
 * Read from the environment for the same reason `VITE_CONSOLE_CONTEXT_PATH` is: the host mounts
 * Dumont under its own prefix, and `/bento` is Turing's. The fallback keeps standalone working
 * without a new entry in every `.env`.
 */
const bentoPath: string = import.meta.env.VITE_BENTO_CONTEXT_PATH ?? "/bento";

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
  BENTO: bentoPath,
  BENTO_HOME: `${bentoPath}/home`,
  BENTO_INTEGRATION_INSTANCE: `${bentoPath}/integration/instance`,
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
  /**
   * The console-era base, stated rather than derived (D37).
   *
   * A bento surface linking to a not-yet-migrated page must name the console tree explicitly:
   * `INTEGRATION_INSTANCE` reads the *current* path, so from inside `/bento` it resolves to
   * `/bento/integration/instance` and would point at a bento route that does not exist yet. That
   * is the one link the migration is guaranteed to need and the one the getter cannot give.
   */
  CONSOLE_INTEGRATION_INSTANCE: `${consolePath}/integration/instance`,
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
