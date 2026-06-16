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

/* --- Dry-run preview & schema diff (roadmap D14) ----------------------------------------- */

/** Per-field coverage over the sampled records. */
export interface DumPreviewCoverage {
  field: string;
  type: string;
  mandatory: boolean;
  present: number;
  total: number;
  percent: number;
}

/** A sampled record that would be skipped at index time (missing a mandatory field). */
export interface DumPreviewSkipped {
  id: string;
  missingMandatoryFields: string[];
}

/** One field whose declaration changed versus the baseline (e.g. `type STRING→TEXT`). */
export interface DumPreviewFieldChange {
  field: string;
  change: string;
}

/** Declared-schema changes versus the baseline. */
export interface DumPreviewSchemaDiff {
  addedFields: string[];
  removedFields: string[];
  changedFields: DumPreviewFieldChange[];
}

/** One document's status in the diff; `newlyEmptyFields` is populated for a changed doc. */
export interface DumPreviewDocChange {
  id: string;
  status: "added" | "changed" | "removed";
  newlyEmptyFields: string[];
}

/** Add / change / remove counts versus the baseline, scoped to the sample. */
export interface DumPreviewDocumentDiff {
  added: number;
  changed: number;
  removed: number;
  unchanged: number;
  changes: DumPreviewDocChange[];
}

/** A would-be document: id, grounded attributes, and whether it would index. */
export interface DumPreviewSampleRecord {
  id: string;
  attributes: Record<string, unknown>;
  wouldIndex: boolean;
  missingMandatoryFields: string[];
}

/** The dry-run preview report — what a real index run would produce, written nowhere. */
export interface DumStructuredPreview {
  source: string;
  discovered: number;
  sampled: number;
  sampleSize: number;
  coverage: DumPreviewCoverage[];
  skipped: DumPreviewSkipped[];
  schemaDiff: DumPreviewSchemaDiff;
  documentDiff: DumPreviewDocumentDiff;
  samples: DumPreviewSampleRecord[];
  baseline: boolean;
}

/** Result of `POST /infer/preview`. */
export interface DumInferPreviewResult {
  success: boolean;
  preview?: DumStructuredPreview | null;
  message?: string | null;
}
