export interface TurPlugin {
  id: string;
  version: string;
  description: string | null;
  provider: string | null;
  state: string;
  path: string | null;
  filename: string | null;
  error: string | null;
  lastModified: string | null;
}
