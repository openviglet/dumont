import { TurIntegrationAssetsSourceService } from "@/services/integration/integration-assets-source.service";
"use client"
import { ROUTES } from "@/app/routes.const"
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { TurIntegrationAssetsSource } from "@/models/integration/integration-assets-source.model"
import { IconFolder, IconSettings, IconReplace } from "@tabler/icons-react"
import { useMemo, useEffect } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@openviglet/viglet-design-system"
import { FormActions } from "../ui/form-actions"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { SectionCard } from "../ui/section-card"

interface Props { value: TurIntegrationAssetsSource; isNew: boolean; integrationId: string; sourceId: string; tab: string; }

export const IntegrationAssetsSourceForm: React.FC<Props> = ({ value, isNew, integrationId, sourceId, tab }) => {
  const { t } = useTranslation();
  const service = useMemo(() => new TurIntegrationAssetsSourceService(integrationId), [integrationId]);
  const form = useForm<TurIntegrationAssetsSource>({ defaultValues: value });
  const navigate = useNavigate();
  useEffect(() => { form.reset(value); }, [form, value]);

  const sourceBaseRoute = `${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/assets-source/${sourceId}`;
  const handleTabChange = (newTab: string) => navigate(`${sourceBaseRoute}/${newTab}`, { replace: true });
  const sourceInstanceRoute = `${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/assets-source`;

  async function onSubmit(source: TurIntegrationAssetsSource) {
    try {
      if (isNew) {
        const result = await service.create(source);
        if (result) { toast.success(t("forms.common.saved", { name: source.name, feature: t("integration.assetsSources.feature") })); navigate(sourceInstanceRoute); }
        else { toast.error(t("forms.common.formSubmitFailed")); }
      } else {
        const result = await service.update(source);
        if (result) { toast.success(t("forms.common.updated", { name: source.name, feature: t("integration.assetsSources.feature") })); }
        else { toast.error(t("forms.common.formSubmitFailed")); }
      }
    } catch (error) { console.error("Form submission error", error); toast.error(t("forms.common.formSubmitFailed")); }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-6">
        <Tabs value={tab} onValueChange={handleTabChange}>
          <TabsList>
            <TabsTrigger value="general"><IconSettings className="size-4" />{t("forms.assetsSource.general")}</TabsTrigger>
            <TabsTrigger value="paths"><IconFolder className="size-4" />{t("forms.assetsSource.paths")}</TabsTrigger>
            <TabsTrigger value="advanced"><IconReplace className="size-4" />{t("forms.assetsSource.advanced")}</TabsTrigger>
          </TabsList>

          <TabsContent value="general" className="space-y-4 mt-3">
            <SectionCard variant="blue">
              <SectionCard.Header icon={IconSettings} title={t("forms.assetsSource.general")} description={t("forms.assetsSource.generalDesc")} />
              <SectionCard.Content>
                <FormField control={form.control} name="name" rules={{ required: t("forms.common.nameRequired") }}
                  render={({ field }) => (<FormItem><FormLabel>{t("forms.common.name")}</FormLabel><FormControl><Input {...field} placeholder={t("forms.assetsSource.namePlaceholder")} type="text" /></FormControl><FormDescription>{t("forms.assetsSource.nameDesc")}</FormDescription><FormMessage /></FormItem>)} />
                <FormField control={form.control} name="description"
                  render={({ field }) => (<FormItem><FormLabel>{t("forms.common.description")}</FormLabel><FormControl><Input {...field} placeholder={t("forms.assetsSource.descriptionPlaceholder")} type="text" /></FormControl><FormMessage /></FormItem>)} />
                <FormField control={form.control} name="site" rules={{ required: t("forms.assetsSource.siteRequired") }}
                  render={({ field }) => (<FormItem><FormLabel>{t("forms.assetsSource.site")}</FormLabel><FormControl><Input {...field} placeholder="my-sn-site" type="text" /></FormControl><FormDescription>{t("forms.assetsSource.siteDesc")}</FormDescription><FormMessage /></FormItem>)} />
                <div className="grid grid-cols-2 gap-4">
                  <FormField control={form.control} name="locale"
                    render={({ field }) => (<FormItem><FormLabel>{t("forms.assetsSource.locale")}</FormLabel><FormControl><Input {...field} placeholder="en_US" type="text" /></FormControl><FormMessage /></FormItem>)} />
                  <FormField control={form.control} name="contentType"
                    render={({ field }) => (<FormItem><FormLabel>{t("forms.assetsSource.contentType")}</FormLabel><FormControl><Input {...field} placeholder="Static File" type="text" /></FormControl><FormMessage /></FormItem>)} />
                </div>
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>

          <TabsContent value="paths" className="space-y-4 mt-3">
            <SectionCard variant="violet">
              <SectionCard.Header icon={IconFolder} title={t("forms.assetsSource.paths")} description={t("forms.assetsSource.pathsDesc")} />
              <SectionCard.Content>
                <FormField control={form.control} name="sourceDir" rules={{ required: t("forms.assetsSource.sourceDirRequired") }}
                  render={({ field }) => (<FormItem><FormLabel>{t("forms.assetsSource.sourceDir")}</FormLabel><FormControl><Input {...field} placeholder="/opt/documents" type="text" /></FormControl><FormDescription>{t("forms.assetsSource.sourceDirDesc")}</FormDescription><FormMessage /></FormItem>)} />
                <FormField control={form.control} name="prefixFromReplace"
                  render={({ field }) => (<FormItem><FormLabel>{t("forms.assetsSource.prefixFrom")}</FormLabel><FormControl><Input {...field} placeholder="/opt/documents" type="text" /></FormControl><FormDescription>{t("forms.assetsSource.prefixFromDesc")}</FormDescription><FormMessage /></FormItem>)} />
                <FormField control={form.control} name="prefixToReplace"
                  render={({ field }) => (<FormItem><FormLabel>{t("forms.assetsSource.prefixTo")}</FormLabel><FormControl><Input {...field} placeholder="https://docs.example.com/files" type="text" /></FormControl><FormDescription>{t("forms.assetsSource.prefixToDesc")}</FormDescription><FormMessage /></FormItem>)} />
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>

          <TabsContent value="advanced" className="space-y-4 mt-3">
            <SectionCard variant="emerald">
              <SectionCard.Header icon={IconReplace} title={t("forms.assetsSource.advanced")} description={t("forms.assetsSource.advancedDesc")} />
              <SectionCard.Content>
                <div className="grid grid-cols-2 gap-4">
                  <FormField control={form.control} name="chunk"
                    render={({ field }) => (<FormItem><FormLabel>{t("forms.assetsSource.chunk")}</FormLabel><FormControl><Input {...field} placeholder="100" type="number" onChange={e => field.onChange(parseInt(e.target.value) || 0)} /></FormControl><FormMessage /></FormItem>)} />
                  <FormField control={form.control} name="encoding"
                    render={({ field }) => (<FormItem><FormLabel>{t("forms.assetsSource.encoding")}</FormLabel><FormControl><Input {...field} placeholder="UTF-8" type="text" /></FormControl><FormMessage /></FormItem>)} />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <FormField control={form.control} name="fileSizeField"
                    render={({ field }) => (<FormItem><FormLabel>{t("forms.assetsSource.fileSizeField")}</FormLabel><FormControl><Input {...field} placeholder="fileSize" type="text" /></FormControl><FormMessage /></FormItem>)} />
                  <FormField control={form.control} name="fileExtensionField"
                    render={({ field }) => (<FormItem><FormLabel>{t("forms.assetsSource.fileExtensionField")}</FormLabel><FormControl><Input {...field} placeholder="fileExtension" type="text" /></FormControl><FormMessage /></FormItem>)} />
                </div>
                <FormField control={form.control} name="typeInId"
                  render={({ field }) => (<FormItemTwoColumns className="rounded-lg border p-4"><FormItemTwoColumns.Left className="space-y-0.5"><FormItemTwoColumns.Label className="text-base">{t("forms.assetsSource.typeInId")}</FormItemTwoColumns.Label><FormItemTwoColumns.Description>{t("forms.assetsSource.typeInIdDesc")}</FormItemTwoColumns.Description></FormItemTwoColumns.Left><FormItemTwoColumns.Right><FormControl><GradientSwitch checked={field.value} onCheckedChange={field.onChange} /></FormControl></FormItemTwoColumns.Right></FormItemTwoColumns>)} />
                <FormField control={form.control} name="showOutput"
                  render={({ field }) => (<FormItemTwoColumns className="rounded-lg border p-4"><FormItemTwoColumns.Left className="space-y-0.5"><FormItemTwoColumns.Label className="text-base">{t("forms.assetsSource.showOutput")}</FormItemTwoColumns.Label><FormItemTwoColumns.Description>{t("forms.assetsSource.showOutputDesc")}</FormItemTwoColumns.Description></FormItemTwoColumns.Left><FormItemTwoColumns.Right><FormControl><GradientSwitch checked={field.value} onCheckedChange={field.onChange} /></FormControl></FormItemTwoColumns.Right></FormItemTwoColumns>)} />
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>
        </Tabs>
        <FormActions><FormActions.Cancel onClick={() => navigate(sourceInstanceRoute)} /><FormActions.Submit /></FormActions>
      </form>
    </Form>
  )
}
