"use client";
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
import type { TurIntegrationEdsSource } from "@/models/integration/integration-eds-source.model";
import { TurIntegrationEdsSourceService } from "@/services/integration/integration-eds-source.service";
import {
  IconCloudComputing,
  IconCode,
  IconDeviceFloppy,
  IconFileDescription,
  IconFilter,
  IconListNumbers,
  IconPlus,
  IconSettings,
  IconTrash,
  IconWorld,
  IconX,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { StickyPageHeader } from "@viglet/viglet-design-system/router";
import { useEffect, useMemo } from "react";
import { useFieldArray, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { DialogDelete } from "../dialog.delete";
import { SNSiteSelect } from "./sn-site-select";
import { GradientButton } from "../ui/gradient-button";
import { SectionCard } from "../ui/section-card";

interface Props {
  value: TurIntegrationEdsSource;
  isNew: boolean;
  integrationId: string;
  sourceId: string;
  tab: string;
  onDelete?: () => void;
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
  headerActions?: React.ReactNode;
}

export const IntegrationEdsSourceForm: React.FC<Props> = ({
  value,
  isNew,
  integrationId,
  sourceId,
  tab,
  onDelete,
  open,
  setOpen,
  headerActions,
}) => {
  const { t } = useTranslation();
  const service = useMemo(
    () => new TurIntegrationEdsSourceService(integrationId),
    [integrationId],
  );
  const form = useForm<TurIntegrationEdsSource>({ defaultValues: value });
  const navigate = useNavigate();

  const indexes = useFieldArray({ control: form.control, name: "indexes" });
  const excludePatterns = useFieldArray({ control: form.control, name: "excludePatterns" });
  const removeSelectors = useFieldArray({ control: form.control, name: "removeSelectors" });
  const facets = useFieldArray({ control: form.control, name: "facets" });
  const fieldMappings = useFieldArray({ control: form.control, name: "fieldMappings" });

  // turSNSites is a plain string[] (Turing SN site names), so it's managed via
  // watch/setValue rather than useFieldArray (which expects objects).
  const turSNSites = form.watch("turSNSites") ?? [];
  const setTurSNSites = (next: string[]) =>
    form.setValue("turSNSites", next, { shouldDirty: true });

  useEffect(() => {
    form.reset(value);
  }, [form, value]);

  const sourceBaseRoute = `${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/eds-source/${sourceId}`;
  const handleTabChange = (newTab: string) =>
    navigate(`${sourceBaseRoute}/${newTab}`, { replace: true });
  const sourceInstanceRoute = `${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/eds-source`;

  async function onSubmit(edsSource: TurIntegrationEdsSource) {
    try {
      if (isNew) {
        const result = await service.create(edsSource);
        if (result) {
          toast.success(
            t("forms.common.saved", {
              name: edsSource.title,
              feature: t("integration.edsSources.feature"),
            }),
          );
          navigate(sourceInstanceRoute);
        } else {
          toast.error(t("forms.common.formSubmitFailed"));
        }
      } else {
        const result = await service.update(edsSource);
        if (result) {
          toast.success(
            t("forms.common.updated", {
              name: edsSource.title,
              feature: t("integration.edsSources.feature"),
            }),
          );
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
          <StickyPageHeader.Title
            icon={IconCloudComputing}
            feature={t("integration.edsSources.title")}
            description={t("integration.edsSources.description")}
          />
          <StickyPageHeader.Actions>
            {open !== undefined && onDelete && setOpen && (
              <DialogDelete
                feature={t("integration.edsSources.title")}
                name={form.watch("title") || ""}
                onDelete={onDelete}
                open={open}
                setOpen={setOpen}
              />
            )}
            {headerActions}
            <GradientButton type="submit" size="sm">
              <IconDeviceFloppy className="size-4" />
              {t("forms.formActions.saveChanges")}
            </GradientButton>
            <GradientButton
              type="button"
              variant="outline"
              size="sm"
              onClick={() => navigate(sourceInstanceRoute)}
            >
              <IconX className="size-4" />
              {t("forms.formActions.cancel")}
            </GradientButton>
          </StickyPageHeader.Actions>
        </StickyPageHeader>
        <Tabs value={tab} onValueChange={handleTabChange}>
          <TabsList>
            <TabsTrigger value="general">
              <IconSettings className="size-4" />
              {t("forms.edsSource.general")}
            </TabsTrigger>
            <TabsTrigger value="indexes">
              <IconListNumbers className="size-4" />
              {t("forms.edsSource.indexes")}
            </TabsTrigger>
            <TabsTrigger value="filters">
              <IconFilter className="size-4" />
              {t("forms.edsSource.filters")}
            </TabsTrigger>
            <TabsTrigger value="extraction">
              <IconCode className="size-4" />
              {t("forms.edsSource.extraction")}
            </TabsTrigger>
            <TabsTrigger value="facets">
              <IconFileDescription className="size-4" />
              {t("forms.edsSource.facets")}
            </TabsTrigger>
          </TabsList>

          {/* General Tab */}
          <TabsContent value="general" className="space-y-4 mt-3">
            <SectionCard variant="blue">
              <SectionCard.Header
                icon={IconSettings}
                title={t("forms.edsSource.general")}
                description={t("forms.edsSource.generalDesc")}
              />
              <SectionCard.Content>
                <FormField
                  control={form.control}
                  name="title"
                  rules={{ required: t("forms.common.nameRequired") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.common.title")}</FormLabel>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder={t("forms.edsSource.titlePlaceholder")}
                          type="text"
                        />
                      </FormControl>
                      <FormDescription>{t("forms.edsSource.titleDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="baseUrl"
                  rules={{ required: t("forms.edsSource.baseUrlRequired") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.edsSource.baseUrl")}</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="https://example.com" type="url" />
                      </FormControl>
                      <FormDescription>{t("forms.edsSource.baseUrlDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="description"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.edsSource.description")}</FormLabel>
                      <FormControl>
                        <Input {...field} type="text" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="locale"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.edsSource.locale")}</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="pt-BR" type="text" />
                      </FormControl>
                      <FormDescription>{t("forms.edsSource.localeDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="userAgent"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.edsSource.userAgent")}</FormLabel>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder="Viglet-Dumont-EDS/1.0"
                          type="text"
                        />
                      </FormControl>
                      <FormDescription>{t("forms.edsSource.userAgentDesc")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <div className="grid grid-cols-3 gap-4">
                  <FormField
                    control={form.control}
                    name="fetchTimeoutMs"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.edsSource.fetchTimeoutMs")}</FormLabel>
                        <FormControl>
                          <Input
                            {...field}
                            placeholder="10000"
                            type="number"
                            onChange={(e) => field.onChange(Number(e.target.value) || 0)}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="requestsPerSecond"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.edsSource.requestsPerSecond")}</FormLabel>
                        <FormControl>
                          <Input
                            {...field}
                            placeholder="50"
                            type="number"
                            onChange={(e) => field.onChange(Number(e.target.value) || 0)}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="maxConcurrent"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.edsSource.maxConcurrent")}</FormLabel>
                        <FormControl>
                          <Input
                            {...field}
                            placeholder="8"
                            type="number"
                            onChange={(e) => field.onChange(Number(e.target.value) || 0)}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </SectionCard.Content>
            </SectionCard>

            <SectionCard variant="emerald">
              <SectionCard.Header
                icon={IconWorld}
                title={t("forms.edsSource.turSNSites")}
                description={t("forms.edsSource.turSNSitesDesc")}
              />
              <SectionCard.Content>
                {turSNSites.map((site, index) => (
                  <div key={`sn-site-${index}`} className="flex items-center gap-2">
                    <SNSiteSelect
                      value={site}
                      onValueChange={(newValue) => {
                        const next = [...turSNSites];
                        next[index] = newValue;
                        setTurSNSites(next);
                      }}
                      integrationId={integrationId}
                      className="flex-1"
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() =>
                        setTurSNSites(turSNSites.filter((_, i) => i !== index))
                      }
                    >
                      <IconTrash className="size-4" />
                    </Button>
                  </div>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setTurSNSites([...turSNSites, ""])}
                >
                  <IconPlus className="size-4 mr-1" />
                  {t("forms.common.add")}
                </Button>
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>

          {/* Indexes Tab */}
          <TabsContent value="indexes" className="space-y-4 mt-3">
            <SectionCard variant="violet">
              <SectionCard.Header
                icon={IconListNumbers}
                title={t("forms.edsSource.indexes")}
                description={t("forms.edsSource.indexesDesc")}
              />
              <SectionCard.Content>
                {indexes.fields.map((field, index) => (
                  <div key={field.id} className="flex items-center gap-2">
                    <Input
                      {...form.register(`indexes.${index}.path`)}
                      placeholder="/query-index.json"
                      className="flex-1 font-mono text-xs"
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() => indexes.remove(index)}
                    >
                      <IconTrash className="size-4" />
                    </Button>
                  </div>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => indexes.append({ id: "", path: "" })}
                >
                  <IconPlus className="size-4 mr-1" />
                  {t("forms.common.add")}
                </Button>
              </SectionCard.Content>
            </SectionCard>

            <SectionCard variant="cyan">
              <SectionCard.Header
                icon={IconCode}
                title={t("forms.edsSource.sitemapFallback")}
                description={t("forms.edsSource.sitemapFallbackDesc")}
              />
              <SectionCard.Content>
                <FormField
                  control={form.control}
                  name="sitemapFallback"
                  render={({ field }) => (
                    <FormItem>
                      <FormControl>
                        <Input {...field} placeholder="/sitemap.xml" type="text" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>

          {/* Filters Tab */}
          <TabsContent value="filters" className="space-y-4 mt-3">
            <SectionCard variant="amber">
              <SectionCard.Header
                icon={IconFilter}
                title={t("forms.edsSource.excludePatterns")}
                description={t("forms.edsSource.excludePatternsDesc")}
              />
              <SectionCard.Content>
                {excludePatterns.fields.map((field, index) => (
                  <div key={field.id} className="flex items-center gap-2">
                    <Input
                      {...form.register(`excludePatterns.${index}.pattern`)}
                      placeholder="/**/baixe-o-app"
                      className="flex-1 font-mono text-xs"
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() => excludePatterns.remove(index)}
                    >
                      <IconTrash className="size-4" />
                    </Button>
                  </div>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => excludePatterns.append({ id: "", pattern: "" })}
                >
                  <IconPlus className="size-4 mr-1" />
                  {t("forms.common.add")}
                </Button>
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>

          {/* Extraction Tab */}
          <TabsContent value="extraction" className="space-y-4 mt-3">
            <SectionCard variant="emerald">
              <SectionCard.Header
                icon={IconCode}
                title={t("forms.edsSource.bodySelector")}
                description={t("forms.edsSource.bodySelectorDesc")}
              />
              <SectionCard.Content>
                <FormField
                  control={form.control}
                  name="bodySelector"
                  render={({ field }) => (
                    <FormItem>
                      <FormControl>
                        <Input {...field} placeholder="main" type="text" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>

            <SectionCard variant="rose">
              <SectionCard.Header
                icon={IconFilter}
                title={t("forms.edsSource.removeSelectors")}
                description={t("forms.edsSource.removeSelectorsDesc")}
              />
              <SectionCard.Content>
                {removeSelectors.fields.map((field, index) => (
                  <div key={field.id} className="flex items-center gap-2">
                    <Input
                      {...form.register(`removeSelectors.${index}.selector`)}
                      placeholder="nav, footer, aside, [role=navigation]"
                      className="flex-1 font-mono text-xs"
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() => removeSelectors.remove(index)}
                    >
                      <IconTrash className="size-4" />
                    </Button>
                  </div>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => removeSelectors.append({ id: "", selector: "" })}
                >
                  <IconPlus className="size-4 mr-1" />
                  {t("forms.common.add")}
                </Button>
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>

          {/* Facets Tab */}
          <TabsContent value="facets" className="space-y-4 mt-3">
            <SectionCard variant="blue">
              <SectionCard.Header
                icon={IconCode}
                title={t("forms.edsSource.facetPathPattern")}
                description={t("forms.edsSource.facetPathPatternDesc")}
              />
              <SectionCard.Content>
                <FormField
                  control={form.control}
                  name="facetPathPattern"
                  render={({ field }) => (
                    <FormItem>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder="/{locale}/{audience}/{section}/**"
                          type="text"
                          className="font-mono text-xs"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>

            <SectionCard variant="violet">
              <SectionCard.Header
                icon={IconFileDescription}
                title={t("forms.edsSource.facets")}
                description={t("forms.edsSource.facetsDesc")}
              />
              <SectionCard.Content>
                {facets.fields.map((field, index) => (
                  <div key={field.id} className="flex items-center gap-2">
                    <Input
                      {...form.register(`facets.${index}.ordinal`, { valueAsNumber: true })}
                      placeholder="0"
                      type="number"
                      className="w-16"
                    />
                    <Input
                      {...form.register(`facets.${index}.name`)}
                      placeholder="audience"
                      className="w-1/4"
                    />
                    <Input
                      {...form.register(`facets.${index}.allowedValues`)}
                      placeholder="pf,pj"
                      className="flex-1"
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() => facets.remove(index)}
                    >
                      <IconTrash className="size-4" />
                    </Button>
                  </div>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    facets.append({
                      id: "",
                      ordinal: facets.fields.length,
                      name: "",
                      allowedValues: "",
                    })
                  }
                >
                  <IconPlus className="size-4 mr-1" />
                  {t("forms.common.add")}
                </Button>
              </SectionCard.Content>
            </SectionCard>

            <SectionCard variant="slate">
              <SectionCard.Header
                icon={IconFileDescription}
                title={t("forms.edsSource.fieldMappings")}
                description={t("forms.edsSource.fieldMappingsDesc")}
              />
              <SectionCard.Content>
                {fieldMappings.fields.map((field, index) => (
                  <div key={field.id} className="flex items-center gap-2">
                    <Input
                      {...form.register(`fieldMappings.${index}.sourceField`)}
                      placeholder="title"
                      className="w-1/3"
                    />
                    <span className="text-muted-foreground">→</span>
                    <Input
                      {...form.register(`fieldMappings.${index}.targetField`)}
                      placeholder="page_title"
                      className="flex-1"
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() => fieldMappings.remove(index)}
                    >
                      <IconTrash className="size-4" />
                    </Button>
                  </div>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    fieldMappings.append({ id: "", sourceField: "", targetField: "" })
                  }
                >
                  <IconPlus className="size-4 mr-1" />
                  {t("forms.common.add")}
                </Button>
              </SectionCard.Content>
            </SectionCard>
          </TabsContent>
        </Tabs>
      </form>
    </Form>
  );
};
