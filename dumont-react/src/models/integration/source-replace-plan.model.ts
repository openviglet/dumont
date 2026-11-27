/**
 * What an import would do to the sources of one connector type (roadmap DU10/DU12/DU14), as
 * `POST .../import?mode=...&dryRun=true` answers it. Names are matched by source name.
 */
export interface SourceReplacePlan {
  create: string[];
  overwrite: string[];
  /** Existing sources the file does not name: a `replace` deletes them, a `merge` keeps them. */
  remove: string[];
  /** Blank, repeated or whitespace-holding names: the import refuses the whole file. */
  invalid: string[];
}

/** `merge` updates the sources a file names and keeps the others; `replace` makes the file the whole set. */
export type SourceImportMode = "merge" | "replace";
