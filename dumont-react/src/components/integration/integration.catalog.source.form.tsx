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
import { IconBuildingBank, IconDeviceFloppy, IconPlugConnected, IconSettings, IconX } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { StickyPageHeader } from "@viglet/viglet-design-system/router";
import { useEffect, useMemo } from "react";
import {
  useForm
} from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { DialogDelete } from "../dialog.delete";
import { GradientButton } from "../ui/gradient-button";
import { SectionCard } from "../ui/section-card";
"use client"

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
        <StickyPageHeader>
          <StickyPageHeader.Title icon={IconBuildingBank} feature={t("integration.catalogSources.title")} description={t("integration.catalogSources.description")} />
          <StickyPageHeader.Actions>
            {open !== undefined && onDelete && setOpen && (
              <DialogDelete feature={t("integration.catalogSources.title")} name={form.watch("name") || ""} onDelete={onDelete} open={open} setOpen={setOpen} />
            )}
            {headerActions}
            <GradientButton type="submit" size="sm">
              <IconDeviceFloppy className="size-4" />
              {t("forms.formActions.saveChanges")}
            </GradientButton>
            <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(sourceInstanceRoute)}>
              <IconX className="size-4" />
              {t("forms.formActions.cancel")}
            </GradientButton>
          </StickyPageHeader.Actions>
        </StickyPageHeader>
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
                  rules={{ required: t("forms.catalogSource.catalogUrlRequired") }}
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
          </TabsContent>
        </Tabs>

      </form>
    </Form>
  )
}
