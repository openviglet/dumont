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
import type { TurIntegrationDbSource } from "@/models/integration/integration-db-source.model";
import { TurIntegrationDbSourceService } from "@/services/integration/integration-db-source.service";
import { IconDatabase, IconFileDescription, IconSettings, IconUpload } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useMemo } from "react";
import {
  useForm
} from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { FormActions } from "../ui/form-actions";
import { FormItemTwoColumns } from "../ui/form-item-two-columns";
import { GradientSwitch } from "../ui/gradient-switch";
import { SectionCard } from "../ui/section-card";
"use client"

interface Props {
  value: TurIntegrationDbSource;
  isNew: boolean;
  integrationId: string;
  sourceId: string;
  tab: string;
}

export const IntegrationDbSourceForm: React.FC<Props> = ({ value, isNew, integrationId, sourceId, tab }) => {
  const { t } = useTranslation();
  const turIntegrationDbSourceService = useMemo(() => new TurIntegrationDbSourceService(integrationId), [integrationId]);
  const form = useForm<TurIntegrationDbSource>({
    defaultValues: value
  });
  const navigate = useNavigate()

  useEffect(() => {
    form.reset(value);
  }, [form, value]);

  const sourceBaseRoute = `${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/db-source/${sourceId}`;
  const handleTabChange = (newTab: string) => {
    navigate(`${sourceBaseRoute}/${newTab}`, { replace: true });
  };

  const sourceInstanceRoute = `${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/db-source`
  async function onSubmit(dbSource: TurIntegrationDbSource) {
    try {
      if (isNew) {
        const result = await turIntegrationDbSourceService.create(dbSource);
        if (result) {
          toast.success(t("forms.common.saved", { name: dbSource.name, feature: t("integration.dbSources.feature") }));
          navigate(sourceInstanceRoute);
        } else {
          toast.error(t("forms.common.formSubmitFailed"));
        }
      } else {
        const result = await turIntegrationDbSourceService.update(dbSource);
        if (result) {
          toast.success(t("forms.common.updated", { name: dbSource.name, feature: t("integration.dbSources.feature") }));
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
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-6">
        <Tabs value={tab} onValueChange={handleTabChange}>
          <TabsList>
            <TabsTrigger value="general">
              <IconSettings className="size-4" />
              {t("forms.dbSource.general")}
            </TabsTrigger>
            <TabsTrigger value="connection">
              <IconDatabase className="size-4" />
              {t("forms.dbSource.connection")}
            </TabsTrigger>
            <TabsTrigger value="fields">
              <IconFileDescription className="size-4" />
              {t("forms.dbSource.fields")}
            </TabsTrigger>
            <TabsTrigger value="advanced">
              <IconUpload className="size-4" />
              {t("forms.dbSource.advanced")}
            </TabsTrigger>
          </TabsList>

          {/* General Tab */}
          <TabsContent value="general" className="space-y-4 mt-3">
            <SectionCard variant="blue">
              <SectionCard.Header icon={IconSettings} title={t("forms.dbSource.general")} description={t("forms.dbSource.generalDesc")} />
              <SectionCard.Content>
                <FormField
                  control={form.control}
                  name="name"
                  rules={{ required: t("forms.common.nameRequired") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.common.name")}</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder={t("forms.dbSource.namePlaceholder")} type="text" />
                      </FormControl>
                      <FormDescription>{t("forms.dbSource.nameDesc")}</FormDescription>
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
                        <Textarea {...field} placeholder={t("forms.dbSource.descriptionPlaceholder")} />
                      </FormControl>
                      <FormDescription>{t("forms.dbSource.descriptionDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="site"
                  rules={{ required: t("forms.dbSource.siteRequired") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.dbSource.site")}</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="my-sn-site" type="text" />
                      </FormControl>
                      <FormDescription>{t("forms.dbSource.siteDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={form.control}
                    name="locale"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.dbSource.locale")}</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder="en_US" type="text" />
                        </FormControl>
                        <FormDescription>{t("forms.dbSource.localeDesc")}</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="contentType"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.dbSource.contentType")}</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder="CONTENT_TYPE" type="text" />
                        </FormControl>
                        <FormDescription>{t("forms.dbSource.contentTypeDesc")}</FormDescription>
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
              <SectionCard.Header icon={IconDatabase} title={t("forms.dbSource.connection")} description={t("forms.dbSource.connectionDesc")} />
              <SectionCard.Content>
                <FormField
                  control={form.control}
                  name="driver"
                  rules={{ required: t("forms.dbSource.driverRequired") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.dbSource.driver")}</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="org.postgresql.Driver" type="text" />
                      </FormControl>
                      <FormDescription>{t("forms.dbSource.driverDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="url"
                  rules={{ required: t("forms.dbSource.urlRequired") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.dbSource.url")}</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="jdbc:postgresql://localhost:5432/mydb" type="text" />
                      </FormControl>
                      <FormDescription>{t("forms.dbSource.urlDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={form.control}
                    name="dbUsername"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.dbSource.dbUsername")}</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder="admin" type="text" />
                        </FormControl>
                        <FormDescription>{t("forms.dbSource.dbUsernameDesc")}</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="dbPassword"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.dbSource.dbPassword")}</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder="••••••••" type="password" />
                        </FormControl>
                        <FormDescription>{t("forms.dbSource.dbPasswordDesc")}</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>

                <FormField
                  control={form.control}
                  name="query"
                  rules={{ required: t("forms.dbSource.queryRequired") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.dbSource.query")}</FormLabel>
                      <FormControl>
                        <Textarea {...field} placeholder="SELECT id, title, content FROM articles" rows={5} className="font-mono text-sm" />
                      </FormControl>
                      <FormDescription>{t("forms.dbSource.queryDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>

          {/* Fields Tab */}
          <TabsContent value="fields" className="space-y-4 mt-3">
            <SectionCard variant="emerald">
              <SectionCard.Header icon={IconFileDescription} title={t("forms.dbSource.fields")} description={t("forms.dbSource.fieldsDesc")} />
              <SectionCard.Content>
                <FormField
                  control={form.control}
                  name="multiValuedFields"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.dbSource.multiValuedFields")}</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="tags,categories" type="text" />
                      </FormControl>
                      <FormDescription>{t("forms.dbSource.multiValuedFieldsDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="multiValuedSeparator"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.dbSource.multiValuedSeparator")}</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="," type="text" />
                      </FormControl>
                      <FormDescription>{t("forms.dbSource.multiValuedSeparatorDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="removeHtmlTagsFields"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.dbSource.removeHtmlTagsFields")}</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="content,summary" type="text" />
                      </FormControl>
                      <FormDescription>{t("forms.dbSource.removeHtmlTagsFieldsDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="typeInId"
                  render={({ field }) => (
                    <FormItemTwoColumns className="rounded-lg border p-4">
                      <FormItemTwoColumns.Left className="space-y-0.5">
                        <FormItemTwoColumns.Label className="text-base">{t("forms.dbSource.typeInId")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.dbSource.typeInIdDesc")}
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
              </SectionCard.Content>
            </SectionCard>

            {/* File Extraction */}
            <SectionCard variant="amber">
              <SectionCard.Header icon={IconUpload} title={t("forms.dbSource.fileExtraction")} description={t("forms.dbSource.fileExtractionDesc")} />
              <SectionCard.Content>
                <FormField
                  control={form.control}
                  name="filePathField"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.dbSource.filePathField")}</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="file_path" type="text" />
                      </FormControl>
                      <FormDescription>{t("forms.dbSource.filePathFieldDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={form.control}
                    name="fileContentField"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.dbSource.fileContentField")}</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder="file_content" type="text" />
                        </FormControl>
                        <FormDescription>{t("forms.dbSource.fileContentFieldDesc")}</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="fileSizeField"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.dbSource.fileSizeField")}</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder="file_size" type="text" />
                        </FormControl>
                        <FormDescription>{t("forms.dbSource.fileSizeFieldDesc")}</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>

          {/* Advanced Tab */}
          <TabsContent value="advanced" className="space-y-4 mt-3">
            <SectionCard variant="blue">
              <SectionCard.Header icon={IconSettings} title={t("forms.dbSource.advanced")} description={t("forms.dbSource.advancedDesc")} />
              <SectionCard.Content>
                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={form.control}
                    name="chunk"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.dbSource.chunk")}</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder="100" type="number" onChange={(e) => field.onChange(parseInt(e.target.value) || 0)} />
                        </FormControl>
                        <FormDescription>{t("forms.dbSource.chunkDesc")}</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="maxContentMegaByteSize"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.dbSource.maxContentSize")}</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder="5" type="number" onChange={(e) => field.onChange(parseInt(e.target.value) || 0)} />
                        </FormControl>
                        <FormDescription>{t("forms.dbSource.maxContentSizeDesc")}</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>

                <FormField
                  control={form.control}
                  name="encoding"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.dbSource.encoding")}</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="UTF-8" type="text" />
                      </FormControl>
                      <FormDescription>{t("forms.dbSource.encodingDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="customClassName"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.dbSource.customClassName")}</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="com.example.MyCustomProcessor" type="text" />
                      </FormControl>
                      <FormDescription>{t("forms.dbSource.customClassNameDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="deindexBeforeImporting"
                  render={({ field }) => (
                    <FormItemTwoColumns className="rounded-lg border p-4">
                      <FormItemTwoColumns.Left className="space-y-0.5">
                        <FormItemTwoColumns.Label className="text-base">{t("forms.dbSource.deindexBeforeImporting")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.dbSource.deindexBeforeImportingDesc")}
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

                <FormField
                  control={form.control}
                  name="showOutput"
                  render={({ field }) => (
                    <FormItemTwoColumns className="rounded-lg border p-4">
                      <FormItemTwoColumns.Left className="space-y-0.5">
                        <FormItemTwoColumns.Label className="text-base">{t("forms.dbSource.showOutput")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.dbSource.showOutputDesc")}
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
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>
        </Tabs>

        <FormActions>
          <FormActions.Cancel onClick={() => navigate(sourceInstanceRoute)} />
          <FormActions.Submit />
        </FormActions>
      </form>
    </Form>
  )
}
