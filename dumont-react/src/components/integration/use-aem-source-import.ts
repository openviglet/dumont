import { importSourceFromJson } from "@/components/integration/source-import-export";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import type { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import { toast } from "@viglet/viglet-design-system";
import { useCallback, useRef } from "react";
import { useTranslation } from "react-i18next";

/**
 * Strip every id from an AEM source payload, at every depth.
 *
 * An export — or a wizard's draft — carries the ids of the source it came from, and creating with
 * them either collides with that source or silently edits it. The walk has to reach the leaves:
 * locale paths, attribute specifications, models, and each model's target attributes and their
 * source attributes.
 *
 * Exported because the import flow and the creation wizard both need it (D54). The wizard had its
 * own copy, written with `any` at every level — six of them, and a second place for the walk to
 * fall out of step with the model.
 */
export function stripAemSourceIds(payload: Partial<TurIntegrationAemSource>) {
  delete (payload as { id?: string }).id;
  payload.localePaths?.forEach((lp) => delete (lp as { id?: string }).id);
  payload.attributeSpecifications?.forEach((spec) => delete (spec as { id?: string }).id);
  payload.models?.forEach((model) => {
    delete (model as { id?: string }).id;
    model.targetAttrs?.forEach((ta) => {
      delete (ta as { id?: string }).id;
      ta.sourceAttrs?.forEach((sa) => delete (sa as { id?: string }).id);
    });
  });
}

/**
 * Import an AEM source from a JSON file.
 *
 * Extracted so the console list and the bento list share it rather than each carrying a copy
 * (D46). The two pages differ in what the reader clicks — a dropdown item, a button — and not at
 * all in what happens next, which is the part worth having in one place: a payload that kept its
 * ids would collide with the source it was exported from.
 */
export function useAemSourceImport(
  service: TurIntegrationAemSourceService,
  onImported: (sources: TurIntegrationAemSource[]) => void,
) {
  const { t } = useTranslation();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const open = useCallback(() => fileInputRef.current?.click(), []);

  /**
   * Strip every id from an imported payload, at every depth.
   *
   * An export carries the ids of the source it came from, and creating with them either collides
   * with that source or silently edits it. The walk has to reach the leaves: locale paths,
   * attribute specifications, models, and each model's target attributes and their source
   * attributes.
   */
  const onFileChange = useCallback(
    async (event: React.ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      // Cleared before any await, so picking the same file twice in a row still fires a change.
      event.target.value = "";
      if (!file) return;

      const parsed = importSourceFromJson(await file.text());
      if (!parsed) {
        toast.error(t("forms.importExport.importFailed"));
        return;
      }

      const payload = { ...parsed } as Partial<TurIntegrationAemSource>;
      stripAemSourceIds(payload);
      try {
        const created = await service.create(payload as TurIntegrationAemSource);
        if (!created) {
          toast.error(t("forms.common.formSubmitFailed"));
          return;
        }
        toast.success(t("forms.importExport.importSuccess"));
        onImported(await service.query());
      } catch {
        toast.error(t("forms.common.formSubmitFailed"));
      }
    },
    [service, onImported, t],
  );

  return { fileInputRef, open, onFileChange };
}
