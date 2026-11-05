import { ROUTES } from "@/app/routes.const";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import {
  Input
} from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import type { TurIntegrationCatalogSource } from "@/models/integration/integration-catalog-source.model";
import { TurIntegrationCatalogSourceService } from "@/services/integration/integration-catalog-source.service";
import { IconApi, IconBuildingBank, IconLibrary, IconPlugConnected, IconSettings } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { AdaptiveFormHeader } from "@/components/bento/adaptive-form-header";
import { useEffect, useMemo } from "react";
import {
  useForm
} from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { DialogDelete } from "../dialog.delete";
import { FormItemTwoColumns } from "../ui/form-item-two-columns";
import { GradientSwitch } from "../ui/gradient-switch";
import { AdaptiveSectionCard as SectionCard } from "@viglet/viglet-design-system/bento";

const MANIFEST_PLACEHOLDER = `{
  "name": "jobs",
  "request": { "url": "https://api.example/jobs/{page}?key={secret:appKey}" },
  "paging": { "mode": "page", "size": 50, "maxPages": 20 },
  "records": "results",
  "id": "id",
  "fields": [{ "name": "title", "type": "TEXT", "mandatory": true }]
}`;

/** Blank, or text that parses as a JSON object — what the manifest and secrets fields accept. */
function isJsonObjectOrBlank(text: string | undefined): boolean {
  if (!text?.trim()) return true;
  try {
    const parsed: unknown = JSON.parse(text);
    return typeof parsed === "object" && parsed !== null && !Array.isArray(parsed);
  } catch {
    return false;
  }
}

/** Blank, or an http(s) URL — what the OAI-PMH base URL field accepts. */
function isHttpUrlOrBlank(text: string | undefined): boolean {
  return !text?.trim() || /^https?:\/\/\S+$/i.test(text.trim());
}

/** The catalog source publishes to a single SN site; the form edits it as one `site` field. */
type CatalogFormValues = TurIntegrationCatalogSource & { site?: string };

interface Props {
  value: TurIntegrationCatalogSource;
  isNew: boolean;
  integrationId: string;
  sourceId: string;
  tab: string;
  onDelete?: () => void;
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
  headerActions?: React.ReactNode;
}

export const IntegrationCatalogSourceForm: React.FC<Props> = ({ value, isNew, integrationId, sourceId, tab, onDelete, open, setOpen, headerActions }) => {
  const { t } = useTranslation();
  const turIntegrationCatalogSourceService = useMemo(() => new TurIntegrationCatalogSourceService(integrationId), [integrationId]);
  const form = useForm<CatalogFormValues>({
    defaultValues: value
  });
  const navigate = useNavigate()

  useEffect(() => {
    form.reset({ ...value, site: value.turSNSites?.[0] ?? "" });
  }, [form, value]);

  const sourceBaseRoute = `${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/catalog-source/${sourceId}`;
  const handleTabChange = (newTab: string) => {
    navigate(`${sourceBaseRoute}/${newTab}`, { replace: true });
  };

  const sourceInstanceRoute = `${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/catalog-source`
  async function onSubmit(formValues: CatalogFormValues) {
    const { site, ...rest } = formValues;
    const catalogSource: TurIntegrationCatalogSource = {
      ...rest,
      turSNSites: site && site.trim() ? [site.trim()] : [],
    };
    try {
      if (isNew) {
        const result = await turIntegrationCatalogSourceService.create(catalogSource);
        if (result) {
          toast.success(t("forms.common.saved", { name: catalogSource.name, feature: t("integration.catalogSources.feature") }));
          navigate(sourceInstanceRoute);
        } else {
          toast.error(t("forms.common.formSubmitFailed"));
        }
      } else {
        const result = await turIntegrationCatalogSourceService.update(catalogSource);
        if (result) {
          toast.success(t("forms.common.updated", { name: catalogSource.name, feature: t("integration.catalogSources.feature") }));
        } else {
          toast.error(t("forms.common.formSubmitFailed"));
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
        <AdaptiveFormHeader
          icon={IconBuildingBank}
          feature={t("integration.catalogSources.title")}
          description={t("integration.catalogSources.description")}
          title={form.watch("name") || ""}
          urlBase={sourceInstanceRoute}
          onCancel={() => navigate(sourceInstanceRoute)}
          dirty={isNew || form.formState.isDirty}
          titleMissing={!form.watch("name")?.trim()}
          actions={
            <>
            {open !== undefined && onDelete && setOpen && (
              <DialogDelete feature={t("integration.catalogSources.title")} name={form.watch("name") || ""} onDelete={onDelete} open={open} setOpen={setOpen} />
            )}
            {headerActions}
            </>
          }
        />
        <Tabs value={tab} onValueChange={handleTabChange}>
          <TabsList>
            <TabsTrigger value="general">
              <IconSettings className="size-4" />
              {t("forms.catalogSource.general")}
            </TabsTrigger>
            <TabsTrigger value="connection">
              <IconPlugConnected className="size-4" />
              {t("forms.catalogSource.connection")}
            </TabsTrigger>
          </TabsList>

          {/* General Tab */}
          <TabsContent value="general" className="space-y-4 mt-3">
            <SectionCard variant="blue">
              <SectionCard.Header icon={IconSettings} title={t("forms.catalogSource.general")} description={t("forms.catalogSource.generalDesc")} />
              <SectionCard.Content>
                <FormField
                  control={form.control}
                  name="name"
                  rules={{ required: t("forms.common.nameRequired") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.common.name")}</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder={t("forms.catalogSource.namePlaceholder")} type="text" />
                      </FormControl>
                      <FormDescription>{t("forms.catalogSource.nameDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="description"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.common.description")}</FormLabel>
                      <FormControl>
                        <Textarea {...field} placeholder={t("forms.catalogSource.descriptionPlaceholder")} />
                      </FormControl>
                      <FormDescription>{t("forms.catalogSource.descriptionDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={form.control}
                    name="site"
                    rules={{ required: t("forms.catalogSource.siteRequired") }}
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.catalogSource.site")}</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder="my-sn-site" type="text" />
                        </FormControl>
                        <FormDescription>{t("forms.catalogSource.siteDesc")}</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="locale"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.catalogSource.locale")}</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder="pt_BR" type="text" />
                        </FormControl>
                        <FormDescription>{t("forms.catalogSource.localeDesc")}</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>

          {/* Connection Tab */}
          <TabsContent value="connection" className="space-y-4 mt-3">
            <SectionCard variant="violet">
              <SectionCard.Header icon={IconPlugConnected} title={t("forms.catalogSource.connection")} description={t("forms.catalogSource.connectionDesc")} />
              <SectionCard.Content>
                <FormField
                  control={form.control}
                  name="catalogUrl"
                  rules={{
                    // A JSON API manifest (D92) or an OAI-PMH repository (D315) replaces the catalog
                    // URL, so any one of the three is enough.
                    validate: (url, values) =>
                      Boolean(url?.trim() || values.jsonApiManifest?.trim() || values.oaiPmhBaseUrl?.trim()) ||
                      t("forms.catalogSource.catalogUrlOrManifestRequired"),
                  }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.catalogSource.catalogUrl")}</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="https://engine.example/output/ufmg.json" type="text" />
                      </FormControl>
                      <FormDescription>{t("forms.catalogSource.catalogUrlDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={form.control}
                    name="authHeaderName"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.catalogSource.authHeaderName")}</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder="Authorization" type="text" />
                        </FormControl>
                        <FormDescription>{t("forms.catalogSource.authHeaderNameDesc")}</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="authHeaderValue"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.catalogSource.authHeaderValue")}</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder="Bearer ••••••••" type="password" />
                        </FormControl>
                        <FormDescription>{t("forms.catalogSource.authHeaderValueDesc")}</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </SectionCard.Content>
            </SectionCard>

            <SectionCard variant="emerald">
              <SectionCard.Header icon={IconApi} title={t("forms.catalogSource.jsonApi")} description={t("forms.catalogSource.jsonApiDesc")} />
              <SectionCard.Content>
                <FormField
                  control={form.control}
                  name="jsonApiManifest"
                  rules={{ validate: (manifest) => isJsonObjectOrBlank(manifest) || t("forms.catalogSource.jsonApiManifestInvalid") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.catalogSource.jsonApiManifest")}</FormLabel>
                      <FormControl>
                        <Textarea {...field} value={field.value ?? ""} rows={14} className="font-mono text-xs" placeholder={MANIFEST_PLACEHOLDER} />
                      </FormControl>
                      <FormDescription>{t("forms.catalogSource.jsonApiManifestDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="jsonApiSecrets"
                  rules={{ validate: (secrets) => isJsonObjectOrBlank(secrets) || t("forms.catalogSource.jsonApiSecretsInvalid") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.catalogSource.jsonApiSecrets")}</FormLabel>
                      <FormControl>
                        <Textarea {...field} value={field.value ?? ""} rows={3} className="font-mono text-xs" placeholder='{"appKey": "..."}' />
                      </FormControl>
                      <FormDescription>{t("forms.catalogSource.jsonApiSecretsDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>

            <SectionCard variant="amber">
              <SectionCard.Header icon={IconLibrary} title={t("forms.catalogSource.oaiPmh")} description={t("forms.catalogSource.oaiPmhDesc")} />
              <SectionCard.Content>
                <FormField
                  control={form.control}
                  name="oaiPmhBaseUrl"
                  rules={{
                    validate: (url, values) => {
                      if (!isHttpUrlOrBlank(url)) return t("forms.catalogSource.oaiPmhBaseUrlInvalid");
                      // The API refuses a source with both bindings; say so before the round trip.
                      if (url?.trim() && values.jsonApiManifest?.trim()) return t("forms.catalogSource.oaiPmhOrManifest");
                      return true;
                    },
                  }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.catalogSource.oaiPmhBaseUrl")}</FormLabel>
                      <FormControl>
                        <Input {...field} value={field.value ?? ""} placeholder="https://repositorio.example.edu/oai/request" type="text" />
                      </FormControl>
                      <FormDescription>{t("forms.catalogSource.oaiPmhBaseUrlDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={form.control}
                    name="oaiPmhMetadataPrefix"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.catalogSource.oaiPmhMetadataPrefix")}</FormLabel>
                        <FormControl>
                          <Input {...field} value={field.value ?? ""} placeholder="oai_dc" type="text" />
                        </FormControl>
                        <FormDescription>{t("forms.catalogSource.oaiPmhMetadataPrefixDesc")}</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="oaiPmhSet"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.catalogSource.oaiPmhSet")}</FormLabel>
                        <FormControl>
                          <Input {...field} value={field.value ?? ""} placeholder="com_123456789_1" type="text" />
                        </FormControl>
                        <FormDescription>{t("forms.catalogSource.oaiPmhSetDesc")}</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>

                <FormField
                  control={form.control}
                  name="oaiPmhFullText"
                  render={({ field }) => (
                    <FormItemTwoColumns className="rounded-lg border p-4">
                      <FormItemTwoColumns.Left className="space-y-0.5">
                        <FormItemTwoColumns.Label className="text-base">{t("forms.catalogSource.oaiPmhFullText")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>{t("forms.catalogSource.oaiPmhFullTextDesc")}</FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <GradientSwitch aria-label={t("forms.catalogSource.oaiPmhFullText")} checked={field.value ?? false} onCheckedChange={field.onChange} />
                        </FormControl>
                      </FormItemTwoColumns.Right>
                    </FormItemTwoColumns>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>
        </Tabs>

      </form>
    </Form>
  )
}
