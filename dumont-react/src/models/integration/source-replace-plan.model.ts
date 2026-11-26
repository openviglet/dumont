/**
 * What a replacing import would do to the sources of one connector type (roadmap DU10/DU12),
 * as `POST .../import?mode=replace&dryRun=true` answers it. Names are matched by source name.
 */
export interface SourceReplacePlan {
  create: string[];
  overwrite: string[];
  remove: string[];
  /** Blank, repeated or whitespace-holding names: the replace refuses the whole file. */
  invalid: string[];
}
