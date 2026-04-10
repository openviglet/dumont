import { LanguageSelect } from "@/components/language-select";
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns";
import { GradientSwitch } from "@/components/ui/gradient-switch";
import { Input } from "@/components/ui/input";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import type { TurLocale } from "@/models/locale/locale.model";
import { TurLocaleService } from "@/services/locale/locale.service";
import { useEffect, useState } from "react";
import type { UseFormReturn } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { DynamicSourceLocales } from "../../dynamic.source.locale";
import { SNSiteSelect } from "../../sn-site-select";

interface StepSitesLocalesProps {
  form: UseFormReturn<TurIntegrationAemSource>;
  integrationId: string;
}

const turLocaleService = new TurLocaleService();

export function StepSitesLocales({
  form,
  integrationId,
}: Readonly<StepSitesLocalesProps>) {
  const { t } = useTranslation();
  const [locales, setLocales] = useState<TurLocale[]>([]);
  const authorEnabled = form.watch("author");
  const publishEnabled = form.watch("publish");

  useEffect(() => {
    turLocaleService.query().then(setLocales);
  }, []);

  return (
    <div className="space-y-6 max-w-lg">
      <div>
        <h3 className="text-base font-semibold">
          {t("forms.wizard.stepSitesLocales")}
        </h3>
        <p className="text-sm text-muted-foreground mt-1">
          {t("forms.wizard.stepSitesLocalesDesc")}
        </p>
      </div>

      {/* Author */}
      <div className="rounded-lg border p-4 space-y-3">
        <FormField
          control={form.control}
          name="author"
          render={({ field }) => (
            <FormItemTwoColumns>
              <FormItemTwoColumns.Left className="space-y-0.5">
                <FormItemTwoColumns.Label className="text-base font-semibold">
                  {t("forms.integrationSource.enableAuthor")}
                </FormItemTwoColumns.Label>
                <FormItemTwoColumns.Description>
                  {t("forms.integrationSource.authorConfigDesc")}
                </FormItemTwoColumns.Description>
              </FormItemTwoColumns.Left>
              <FormItemTwoColumns.Right>
                <FormControl>
                  <GradientSwitch
                    checked={field.value}
                    onCheckedChange={(checked) => {
                      field.onChange(checked);
                      if (checked && !form.getValues("authorURLPrefix")) {
                        form.setValue("authorURLPrefix", form.getValues("endpoint"));
                      }
                    }}
                  />
                </FormControl>
              </FormItemTwoColumns.Right>
            </FormItemTwoColumns>
          )}
        />
        {authorEnabled && (
          <div className="space-y-3 pt-2">
            <FormField
              control={form.control}
              name="authorSNSite"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {t("forms.integrationSource.authorSnSite")}
                  </FormLabel>
                  <FormControl>
                    <SNSiteSelect
                      value={field.value}
                      onValueChange={field.onChange}
                      integrationId={integrationId}
                    />
                  </FormControl>
                  <FormDescription>
                    {t("forms.wizard.snSiteDesc")}
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="authorURLPrefix"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {t("forms.integrationSource.authorUrlPrefix")}
                  </FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      placeholder="http://localhost:4502"
                      type="url"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        )}
      </div>

      {/* Publish */}
      <div className="rounded-lg border p-4 space-y-3">
        <FormField
          control={form.control}
          name="publish"
          render={({ field }) => (
            <FormItemTwoColumns>
              <FormItemTwoColumns.Left className="space-y-0.5">
                <FormItemTwoColumns.Label className="text-base font-semibold">
                  {t("forms.integrationSource.enablePublish")}
                </FormItemTwoColumns.Label>
                <FormItemTwoColumns.Description>
                  {t("forms.integrationSource.publishConfigDesc")}
                </FormItemTwoColumns.Description>
              </FormItemTwoColumns.Left>
              <FormItemTwoColumns.Right>
                <FormControl>
                  <GradientSwitch
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                </FormControl>
              </FormItemTwoColumns.Right>
            </FormItemTwoColumns>
          )}
        />
        {publishEnabled && (
          <div className="space-y-3 pt-2">
            <FormField
              control={form.control}
              name="publishSNSite"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {t("forms.integrationSource.publishSnSite")}
                  </FormLabel>
                  <FormControl>
                    <SNSiteSelect
                      value={field.value}
                      onValueChange={field.onChange}
                      integrationId={integrationId}
                    />
                  </FormControl>
                  <FormDescription>
                    {t("forms.wizard.snSiteDesc")}
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="publishURLPrefix"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {t("forms.integrationSource.publishUrlPrefix")}
                  </FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      placeholder="https://www.mysite.com"
                      type="url"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        )}
      </div>

      {/* Locales */}
      <div className="space-y-3">
        <h4 className="text-sm font-semibold">
          {t("forms.integrationSource.localesConfig")}
        </h4>
        <FormField
          control={form.control}
          name="defaultLocale"
          render={({ field }) => (
            <FormItem>
              <FormLabel>
                {t("forms.integrationSource.defaultLocale")}
              </FormLabel>
              <FormControl>
                <LanguageSelect
                  value={field.value}
                  onValueChange={field.onChange}
                  locales={locales}
                  extraLocaleValues={field.value ? [field.value] : []}
                  className="w-full"
                />
              </FormControl>
              <FormDescription>
                {t("forms.wizard.defaultLocaleDesc")}
              </FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />

        <div className="rounded-lg border p-3 bg-muted/30">
          <h4 className="font-medium text-sm mb-2">
            {t("forms.integrationSource.localePaths")}
          </h4>
          <p className="text-xs text-muted-foreground mb-3">
            {t("forms.integrationSource.localePathsDesc")}
          </p>
          <DynamicSourceLocales
            fieldName="localePaths"
            control={form.control}
            register={form.register}
            connection={{
              endpoint: form.getValues("endpoint"),
              username: form.getValues("username"),
              password: form.getValues("password"),
            }}
            integrationId={integrationId}
            rootPath={form.getValues("rootPath")}
          />
        </div>
      </div>
    </div>
  );
}
