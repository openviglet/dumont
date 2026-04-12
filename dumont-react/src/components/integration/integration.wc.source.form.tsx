import { ROUTES } from "@/app/routes.const";
import { Button } from "@/components/ui/button";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { TurIntegrationWcSource } from "@/models/integration/integration-wc-source.model";
import { TurIntegrationWcSourceService } from "@/services/integration/integration-wc-source.service";
import { IconFileDescription, IconFilter, IconGlobe, IconPlus, IconSettings, IconTrash } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useMemo } from "react";
import { useFieldArray, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { StickySaveBar } from "../ui/sticky-save-bar";
import { SectionCard } from "../ui/section-card";
"use client"

interface Props {
  value: TurIntegrationWcSource;
  isNew: boolean;
  integrationId: string;
  sourceId: string;
  tab: string;
}

export const IntegrationWcSourceForm: React.FC<Props> = ({ value, isNew, integrationId, sourceId, tab }) => {
  const { t } = useTranslation();
  const service = useMemo(() => new TurIntegrationWcSourceService(integrationId), [integrationId]);
  const form = useForm<TurIntegrationWcSource>({ defaultValues: value });
  const navigate = useNavigate();

  const startingPoints = useFieldArray({ control: form.control, name: "startingPoints" });
  const allowUrls = useFieldArray({ control: form.control, name: "allowUrls" });
  const notAllowUrls = useFieldArray({ control: form.control, name: "notAllowUrls" });
  const notAllowExtensions = useFieldArray({ control: form.control, name: "notAllowExtensions" });
  const attributeMappings = useFieldArray({ control: form.control, name: "attributeMappings" });

  useEffect(() => { form.reset(value); }, [form, value]);

  const sourceBaseRoute = `${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/wc-source/${sourceId}`;
  const handleTabChange = (newTab: string) => navigate(`${sourceBaseRoute}/${newTab}`, { replace: true });
  const sourceInstanceRoute = `${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/wc-source`;

  async function onSubmit(wcSource: TurIntegrationWcSource) {
    try {
      if (isNew) {
        const result = await service.create(wcSource);
        if (result) {
          toast.success(t("forms.common.saved", { name: wcSource.title, feature: t("integration.wcSources.feature") }));
          navigate(sourceInstanceRoute);
        } else { toast.error(t("forms.common.formSubmitFailed")); }
      } else {
        const result = await service.update(wcSource);
        if (result) {
          toast.success(t("forms.common.updated", { name: wcSource.title, feature: t("integration.wcSources.feature") }));
        } else { toast.error(t("forms.common.formSubmitFailed")); }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-6">
        <StickySaveBar
          title={form.watch("title") || (isNew ? t("integration.wcSources.newSource") : t("integration.wcSources.title"))}
          onCancel={() => navigate(sourceInstanceRoute)}
        />
        <Tabs value={tab} onValueChange={handleTabChange}>
          <TabsList>
            <TabsTrigger value="general"><IconSettings className="size-4" />{t("forms.wcSource.general")}</TabsTrigger>
            <TabsTrigger value="crawling"><IconGlobe className="size-4" />{t("forms.wcSource.crawling")}</TabsTrigger>
            <TabsTrigger value="filters"><IconFilter className="size-4" />{t("forms.wcSource.filters")}</TabsTrigger>
            <TabsTrigger value="attributes"><IconFileDescription className="size-4" />{t("forms.wcSource.attributes")}</TabsTrigger>
          </TabsList>

          {/* General Tab */}
          <TabsContent value="general" className="space-y-4 mt-3">
            <SectionCard variant="blue">
              <SectionCard.Header icon={IconSettings} title={t("forms.wcSource.general")} description={t("forms.wcSource.generalDesc")} />
              <SectionCard.Content>
                <FormField control={form.control} name="title" rules={{ required: t("forms.common.nameRequired") }}
                  render={({ field }) => (
                    <FormItem><FormLabel>{t("forms.common.title")}</FormLabel>
                      <FormControl><Input {...field} placeholder={t("forms.wcSource.titlePlaceholder")} type="text" /></FormControl>
                      <FormDescription>{t("forms.wcSource.titleDesc")}</FormDescription><FormMessage /></FormItem>
                  )} />
                <FormField control={form.control} name="url" rules={{ required: t("forms.wcSource.urlRequired") }}
                  render={({ field }) => (
                    <FormItem><FormLabel>{t("forms.wcSource.url")}</FormLabel>
                      <FormControl><Input {...field} placeholder="https://en.wikipedia.org" type="url" /></FormControl>
                      <FormDescription>{t("forms.wcSource.urlDesc")}</FormDescription><FormMessage /></FormItem>
                  )} />
                <div className="grid grid-cols-2 gap-4">
                  <FormField control={form.control} name="username"
                    render={({ field }) => (
                      <FormItem><FormLabel>{t("forms.wcSource.username")}</FormLabel>
                        <FormControl><Input {...field} placeholder="admin" type="text" /></FormControl>
                        <FormDescription>{t("forms.wcSource.usernameDesc")}</FormDescription><FormMessage /></FormItem>
                    )} />
                  <FormField control={form.control} name="password"
                    render={({ field }) => (
                      <FormItem><FormLabel>{t("forms.wcSource.password")}</FormLabel>
                        <FormControl><Input {...field} placeholder="••••••••" type="password" /></FormControl>
                        <FormDescription>{t("forms.wcSource.passwordDesc")}</FormDescription><FormMessage /></FormItem>
                    )} />
                </div>
                <FormField control={form.control} name="locale"
                  render={({ field }) => (
                    <FormItem><FormLabel>{t("forms.wcSource.locale")}</FormLabel>
                      <FormControl><Input {...field} placeholder="en_US" type="text" /></FormControl>
                      <FormDescription>{t("forms.wcSource.localeDesc")}</FormDescription><FormMessage /></FormItem>
                  )} />
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>

          {/* Crawling Tab */}
          <TabsContent value="crawling" className="space-y-4 mt-3">
            <SectionCard variant="violet">
              <SectionCard.Header icon={IconGlobe} title={t("forms.wcSource.startingPoints")} description={t("forms.wcSource.startingPointsDesc")} />
              <SectionCard.Content>
                {startingPoints.fields.map((field, index) => (
                  <div key={field.id} className="flex items-center gap-2">
                    <Input {...form.register(`startingPoints.${index}.url`)} placeholder="https://en.wikipedia.org/wiki/Main_Page" className="flex-1" />
                    <Button type="button" variant="ghost" size="icon" onClick={() => startingPoints.remove(index)}><IconTrash className="size-4" /></Button>
                  </div>
                ))}
                <Button type="button" variant="outline" size="sm" onClick={() => startingPoints.append({ id: "", url: "" })}>
                  <IconPlus className="size-4 mr-1" />{t("forms.common.add")}
                </Button>
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>

          {/* Filters Tab */}
          <TabsContent value="filters" className="space-y-4 mt-3">
            <SectionCard variant="emerald">
              <SectionCard.Header icon={IconFilter} title={t("forms.wcSource.allowUrls")} description={t("forms.wcSource.allowUrlsDesc")} />
              <SectionCard.Content>
                {allowUrls.fields.map((field, index) => (
                  <div key={field.id} className="flex items-center gap-2">
                    <Input {...form.register(`allowUrls.${index}.url`)} placeholder="/wiki/*" className="flex-1" />
                    <Button type="button" variant="ghost" size="icon" onClick={() => allowUrls.remove(index)}><IconTrash className="size-4" /></Button>
                  </div>
                ))}
                <Button type="button" variant="outline" size="sm" onClick={() => allowUrls.append({ id: "", url: "" })}>
                  <IconPlus className="size-4 mr-1" />{t("forms.common.add")}
                </Button>
              </SectionCard.Content>
            </SectionCard>

            <SectionCard variant="amber">
              <SectionCard.Header icon={IconFilter} title={t("forms.wcSource.notAllowUrls")} description={t("forms.wcSource.notAllowUrlsDesc")} />
              <SectionCard.Content>
                {notAllowUrls.fields.map((field, index) => (
                  <div key={field.id} className="flex items-center gap-2">
                    <Input {...form.register(`notAllowUrls.${index}.url`)} placeholder="/wiki/Special:*" className="flex-1" />
                    <Button type="button" variant="ghost" size="icon" onClick={() => notAllowUrls.remove(index)}><IconTrash className="size-4" /></Button>
                  </div>
                ))}
                <Button type="button" variant="outline" size="sm" onClick={() => notAllowUrls.append({ id: "", url: "" })}>
                  <IconPlus className="size-4 mr-1" />{t("forms.common.add")}
                </Button>
              </SectionCard.Content>
            </SectionCard>

            <SectionCard variant="blue">
              <SectionCard.Header icon={IconFilter} title={t("forms.wcSource.notAllowExtensions")} description={t("forms.wcSource.notAllowExtensionsDesc")} />
              <SectionCard.Content>
                {notAllowExtensions.fields.map((field, index) => (
                  <div key={field.id} className="flex items-center gap-2">
                    <Input {...form.register(`notAllowExtensions.${index}.extension`)} placeholder=".pdf" className="flex-1" />
                    <Button type="button" variant="ghost" size="icon" onClick={() => notAllowExtensions.remove(index)}><IconTrash className="size-4" /></Button>
                  </div>
                ))}
                <Button type="button" variant="outline" size="sm" onClick={() => notAllowExtensions.append({ id: "", extension: "" })}>
                  <IconPlus className="size-4 mr-1" />{t("forms.common.add")}
                </Button>
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>

          {/* Attributes Tab */}
          <TabsContent value="attributes" className="space-y-4 mt-3">
            <SectionCard variant="violet">
              <SectionCard.Header icon={IconFileDescription} title={t("forms.wcSource.attributeMappings")} description={t("forms.wcSource.attributeMappingsDesc")} />
              <SectionCard.Content>
                {attributeMappings.fields.map((field, index) => (
                  <div key={field.id} className="flex items-center gap-2">
                    <Input {...form.register(`attributeMappings.${index}.name`)} placeholder="title" className="w-1/4" />
                    <Input {...form.register(`attributeMappings.${index}.className`)} placeholder="com.viglet...ExtTitle" className="flex-1 font-mono text-xs" />
                    <Input {...form.register(`attributeMappings.${index}.text`)} placeholder="literal text" className="w-1/4" />
                    <Button type="button" variant="ghost" size="icon" onClick={() => attributeMappings.remove(index)}><IconTrash className="size-4" /></Button>
                  </div>
                ))}
                <Button type="button" variant="outline" size="sm" onClick={() => attributeMappings.append({ id: "", name: "", className: "", text: "" })}>
                  <IconPlus className="size-4 mr-1" />{t("forms.common.add")}
                </Button>
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>
        </Tabs>

      </form>
    </Form>
  )
}
