/**
 * Types for AI-assisted source inference (roadmap D13).
 *
 * Mirrors the backend `DumSourceDraft` / `DumInferredField` shape returned by
 * `POST /api/v2/connector/source/infer`. The draft is editable in the wizard and
 * posted back to `/infer/index` only after a human confirms it — it never
 * auto-publishes.
 */

export type DumSourceShape =
  | "SITEMAP"
  | "HTML_LISTING"
  | "TABULAR_LISTING"
  | "UNKNOWN";

/** Search-engine field types accepted by the manifest (Turing `TurSEFieldType`). */
export type TurSEFieldType =
  | "STRING"
  | "TEXT"
  | "INT"
  | "LONG"
  | "DATE"
  | "BOOL";

export const FIELD_TYPES: TurSEFieldType[] = [
  "STRING",
  "TEXT",
  "INT",
  "LONG",
  "DATE",
  "BOOL",
];

/** One proposed (editable) field: manifest spec + structural extraction hint. */
export interface DumInferredField {
  name: string;
  type: TurSEFieldType;
  multiValued: boolean;
  facet: boolean;
  mandatory: boolean;
  description?: string | null;
  columnIndex?: number | null;
  selector?: string | null;
  attribute?: string | null;
}

/** The editable draft proposed by inference. */
export interface DumSourceDraft {
  shape: DumSourceShape;
  confidence: number;
  strategyConfig: Record<string, string>;
  fields: DumInferredField[];
  notes?: string | null;
}

/** Result of indexing a confirmed draft. */
export interface DumInferIndexResult {
  success: boolean;
  source?: string | null;
  message?: string | null;
}
