import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import {
  IconFile,
  IconPuzzle,
  IconTemplate,
} from "@tabler/icons-react";
import type { UseFormReturn } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { AemPathBrowser } from "../../aem-path-browser";

interface StepContentProps {
  form: UseFormReturn<TurIntegrationAemSource>;
  integrationId: string;
  presetKey: string;
  onPresetChange: (key: string) => void;
}

const CONTENT_TYPE_OPTIONS = [
  {
    key: "cq-page",
    icon: IconTemplate,
    labelKey: "forms.wizard.contentTypePage",
    descKey: "forms.wizard.contentTypePageDesc",
  },
  {
    key: "content-fragment",
    icon: IconPuzzle,
    labelKey: "forms.wizard.contentTypeCF",
    descKey: "forms.wizard.contentTypeCFDesc",
  },
  {
    key: "custom",
    icon: IconFile,
    labelKey: "forms.wizard.contentTypeCustom",
    descKey: "forms.wizard.contentTypeCustomDesc",
  },
];

export function StepContent({
  form,
  integrationId,
  presetKey,
  onPresetChange,
}: Readonly<StepContentProps>) {
  const { t } = useTranslation();

  const handlePresetSelect = (key: string) => {
    onPresetChange(key);
    if (key === "cq-page") {
      form.setValue("contentType", "cq:Page");
      form.setValue("subType", "cq:PageContent");
    } else if (key === "content-fragment") {
      form.setValue("contentType", "dam:Asset");
      form.setValue("subType", "content-fragment");
    } else {
      form.setValue("contentType", "");
      form.setValue("subType", "");
    }
  };

  const connection = {
    endpoint: form.getValues("endpoint"),
    username: form.getValues("username"),
    password: form.getValues("password"),
  };

  const browseStartPath = presetKey === "content-fragment" ? "/content/dam" : "/content";

  return (
    <div className="space-y-4 max-w-lg">
      <div>
        <h3 className="text-base font-semibold">
          {t("forms.wizard.stepContent")}
        </h3>
        <p className="text-sm text-muted-foreground mt-1">
          {t("forms.wizard.stepContentDesc")}
        </p>
      </div>

      <div>
        <FormLabel className="text-sm font-medium">
          {t("forms.integrationSource.contentType")}
        </FormLabel>
        <div className="grid grid-cols-3 gap-3 mt-2">
          {CONTENT_TYPE_OPTIONS.map((option) => {
            const Icon = option.icon;
            const isSelected = presetKey === option.key;
            return (
              <button
                key={option.key}
                type="button"
                onClick={() => handlePresetSelect(option.key)}
                className={[
                  "flex flex-col items-center gap-2 rounded-lg border p-4 text-center transition-all cursor-pointer",
                  isSelected
                    ? "border-primary bg-primary/5 ring-2 ring-primary/20"
                    : "border-border hover:border-muted-foreground/30 hover:bg-accent/50",
                ].join(" ")}
              >
                <Icon
                  className={`size-6 ${isSelected ? "text-primary" : "text-muted-foreground"}`}
                />
                <div>
                  <div className="text-sm font-medium">{t(option.labelKey)}</div>
                  <div className="text-xs text-muted-foreground mt-0.5">
                    {t(option.descKey)}
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {presetKey === "custom" && (
        <div className="grid grid-cols-2 gap-4">
          <FormField
            control={form.control}
            name="contentType"
            rules={{
              required: t("forms.integrationSource.modelTypeRequired"),
            }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t("forms.integrationSource.contentType")}</FormLabel>
                <FormControl>
                  <Input {...field} placeholder="cq:Page" type="text" />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="subType"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t("forms.integrationSource.subType")}</FormLabel>
                <FormControl>
                  <Input {...field} placeholder="cq:PageContent" type="text" />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
      )}

      <FormField
        control={form.control}
        name="rootPath"
        rules={{
          required: t("forms.integrationSource.rootPath") + " is required.",
        }}
        render={({ field }) => (
          <FormItem>
            <FormLabel>{t("forms.integrationSource.rootPath")}</FormLabel>
            <FormControl>
              <AemPathBrowser
                value={field.value}
                onChange={field.onChange}
                connection={connection}
                integrationId={integrationId}
                startPath={browseStartPath}
              />
            </FormControl>
            <FormDescription>{t("forms.wizard.rootPathDesc")}</FormDescription>
            <FormMessage />
          </FormItem>
        )}
      />

      <FormField
        control={form.control}
        name="oncePattern"
        render={({ field }) => (
          <FormItem>
            <FormLabel>{t("forms.integrationSource.oncePattern")}</FormLabel>
            <FormControl>
              <Input
                {...field}
                placeholder="^/content/mysite/faqs"
                type="text"
              />
            </FormControl>
            <FormDescription>
              {t("forms.wizard.oncePatternDesc")}
            </FormDescription>
            <FormMessage />
          </FormItem>
        )}
      />
    </div>
  );
}
