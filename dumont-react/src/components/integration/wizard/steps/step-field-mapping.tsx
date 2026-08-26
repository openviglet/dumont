import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import type { TurIntegrationAemAttributeSpec } from "@/models/integration/integration-aem-attribute-spec.model";
import {
  IconAsterisk,
  IconFileDescription,
  IconFilter,
  IconStack2,
} from "@tabler/icons-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { UseFormReturn } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { getPresetByKey } from "../aem-source-presets";

interface StepFieldMappingProps {
  form: UseFormReturn<TurIntegrationAemSource>;
  presetKey: string;
}

interface SpecToggle {
  spec: Omit<TurIntegrationAemAttributeSpec, "id">;
  enabled: boolean;
}

const TYPE_COLORS: Record<string, string> = {
  TEXT: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  STRING: "bg-violet-100 text-violet-700 dark:bg-violet-900 dark:text-violet-300",
  DATE: "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300",
  BOOL: "bg-emerald-100 text-emerald-700 dark:bg-emerald-900 dark:text-emerald-300",
  INT: "bg-rose-100 text-rose-700 dark:bg-rose-900 dark:text-rose-300",
};

export function StepFieldMapping({
  form,
  presetKey,
}: Readonly<StepFieldMappingProps>) {
  const { t } = useTranslation();
  /*
   * D53 - the preset is a lookup, and the toggles are a default the reader can override.
   *
   * Both used to be state an effect rewrote whenever `presetKey` changed - a synchronous write in
   * an effect body, and one that had to remember to clear `specs` in an `else` branch. The preset
   * is a pure lookup, so it is computed; the toggles are stored against the preset they were made
   * for, so switching preset makes them stale rather than needing to be reset.
   */
  const preset = useMemo(() => getPresetByKey(presetKey), [presetKey]);

  const defaultSpecs = useMemo<SpecToggle[]>(
    () => preset?.attributeSpecifications.map((spec) => ({ spec, enabled: true })) ?? [],
    [preset],
  );

  const [toggled, setToggled] = useState<{ forPreset: string; specs: SpecToggle[] } | null>(null);
  const specs = toggled?.forPreset === presetKey ? toggled.specs : defaultSpecs;

  /** Record a new toggle set against the preset it belongs to. */
  const updateSpecs = (next: SpecToggle[]) => setToggled({ forPreset: presetKey, specs: next });

  const syncToForm = useCallback(
    (updatedSpecs: SpecToggle[]) => {
      const enabled = updatedSpecs
        .filter((s) => s.enabled)
        .map((s) => ({ ...s.spec, id: "" }) as TurIntegrationAemAttributeSpec);
      form.setValue("attributeSpecifications", enabled);

      if (preset) {
        form.setValue(
          "models",
          preset.models.map((m) => ({ ...m, id: "" })),
        );
        form.setValue("localeClass", preset.localeClass);
        form.setValue("deltaClass", preset.deltaClass);
      }
    },
    [form, preset],
  );

  useEffect(() => {
    if (specs.length > 0) {
      syncToForm(specs);
    }
  }, [specs, syncToForm]);

  const toggleSpec = (index: number) => {
    const next = [...specs];
    next[index] = { ...next[index], enabled: !next[index].enabled };
    updateSpecs(next);
  };

  const enabledCount = specs.filter((s) => s.enabled).length;

  return (
    <div className="space-y-4 max-w-2xl">
      <div>
        <h3 className="text-base font-semibold">
          {t("forms.wizard.stepFieldMapping")}
        </h3>
        <p className="text-sm text-muted-foreground mt-1">
          {t("forms.wizard.stepFieldMappingDesc")}
        </p>
      </div>

      {specs.length === 0 ? (
        <div className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
          <IconFileDescription className="size-8 mx-auto mb-2 text-muted-foreground/50" />
          {t("forms.wizard.noPresetFields")}
        </div>
      ) : (
        <>
          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">
              {t("forms.wizard.fieldsSelected", { count: enabledCount, total: specs.length })}
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                className="text-xs text-primary hover:underline cursor-pointer"
                onClick={() =>
                  updateSpecs(specs.map((s) => ({ ...s, enabled: true })))
                }
              >
                {t("forms.common.all")}
              </button>
            </div>
          </div>

          <div className="rounded-lg border divide-y">
            {specs.map((item, index) => (
              <div
                key={item.spec.name}
                className={[
                  "flex items-center gap-3 px-3 py-2.5 transition-colors",
                  item.enabled
                    ? "bg-background"
                    : "bg-muted/30 opacity-60",
                ].join(" ")}
              >
                <Checkbox
                  checked={item.enabled}
                  onCheckedChange={() => toggleSpec(index)}
                />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium">
                      {item.spec.name}
                    </span>
                    <Badge
                      variant="outline"
                      className={`text-[10px] px-1.5 py-0 h-4 ${TYPE_COLORS[item.spec.type] ?? ""}`}
                    >
                      {item.spec.type}
                    </Badge>
                    {item.spec.mandatory && (
                      <IconAsterisk className="size-3 text-destructive" />
                    )}
                    {item.spec.multiValued && (
                      <IconStack2 className="size-3 text-muted-foreground" />
                    )}
                    {item.spec.facet && (
                      <IconFilter className="size-3 text-blue-500" />
                    )}
                  </div>
                  <p className="text-xs text-muted-foreground truncate">
                    {item.spec.description}
                    {item.spec.className && (
                      <span className="ml-1 opacity-50">
                        ({item.spec.className.split(".").pop()})
                      </span>
                    )}
                  </p>
                </div>
              </div>
            ))}
          </div>

          {preset && preset.models.length > 0 && (
            <div className="rounded-lg border bg-muted/30 p-3">
              <h4 className="text-sm font-medium mb-1">
                {t("forms.wizard.modelsIncluded")}
              </h4>
              <p className="text-xs text-muted-foreground">
                {t("forms.wizard.modelsIncludedDesc", {
                  count: preset.models.length,
                  type: preset.models.map((m) => m.type).join(", "),
                })}
              </p>
            </div>
          )}
        </>
      )}
    </div>
  );
}
