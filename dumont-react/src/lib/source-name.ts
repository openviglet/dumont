import type { TFunction } from "i18next";

/**
 * A source name is the path segment of the index, reindex, audit and monitoring URLs
 * (`/api/v2/connector/index/{name}` and the rest), so a name holding whitespace saves fine
 * and then fails on the first run (roadmap DU3). Every form that sets a source name passes
 * this as its `validate` rule, so the operator is told before the source is saved.
 */
export function sourceNameHasNoWhitespace(t: TFunction) {
  return (value: string | undefined) =>
    !/\s/.test(value ?? "") || t("forms.common.nameNoWhitespace");
}
