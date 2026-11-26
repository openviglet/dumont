import type { TFunction } from "i18next";

/**
 * A source name is the key of its source in the connector URLs — `index/{name}`, `reindex/{name}`,
 * `audit/{source}`, the monitoring endpoints — so a name with whitespace saves and then fails the
 * first run (D361). Every form that sets a source name validates it with these rules.
 */
export const SOURCE_NAME_WHITESPACE = /\s/;

export function isValidSourceName(value: unknown): boolean {
  return typeof value !== "string" || !SOURCE_NAME_WHITESPACE.test(value);
}

export function sourceNameRules(t: TFunction, requiredMessage: string = t("forms.common.nameRequired")) {
  return {
    required: requiredMessage,
    validate: (value: unknown) => isValidSourceName(value) || t("forms.common.nameNoSpaces"),
  };
}
