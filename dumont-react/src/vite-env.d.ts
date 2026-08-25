/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Host prefix for the console-era tree (`/admin`). */
  readonly VITE_CONSOLE_CONTEXT_PATH: string;
  /** Host prefix for the bento tree (D37). Optional — defaults to `/bento`. */
  readonly VITE_BENTO_CONTEXT_PATH?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
