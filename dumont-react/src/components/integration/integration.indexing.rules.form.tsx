"use client"

import { toast } from "@viglet/viglet-design-system"
import { StickyPageHeader } from "@viglet/viglet-design-system/router"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useForm } from "react-hook-form"
import { useNavigate } from "react-router-dom"

import { ROUTES } from "@/app/routes.const"
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import type { TurIntegrationIndexingRule } from "@/models/integration/integration-indexing-rule.model"
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model"
import type { TurSNSite } from "@/models/sn/sn-site.model"
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service"
import { TurIntegrationAssetsSourceService } from "@/services/integration/integration-assets-source.service"
import { TurIntegrationDbSourceService } from "@/services/integration/integration-db-source.service"
import { TurIntegrationIndexingRuleService } from "@/services/integration/integration-indexing-rule.service"
import { TurIntegrationWcSourceService } from "@/services/integration/integration-wc-source.service"
import { TurSNFieldService } from "@/services/sn/sn.field.service"
import { TurSNSiteService } from "@/services/sn/sn.service"
import { IconDeviceFloppy, IconFileText, IconGavel, IconList, IconMap, IconTools, IconX } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { DialogDelete } from "../dialog.delete"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientButton } from "../ui/gradient-button"
import { SectionCard } from "../ui/section-card"
import { DynamicIndexingRuleFields } from "./dynamic.indexing.rule.field"
// Constants
const RULE_TYPES = [
  { value: "IGNORE", label: "Ignore" }
] as const;

// Props interface with descriptive naming
interface IntegrationIndexingRulesFormProps {
  value: TurIntegrationIndexingRule;
  integrationId: string;
  isNew: boolean;
  onDelete?: () => void;
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
}

/** Internal form shape: values wrapped as `{ value }` so `useFieldArray` can manage them. */
type IndexingRuleFormShape = Omit<TurIntegrationIndexingRule, "values"> & {
  values: { value: string }[];
};

/*
 * D56 - hoisted out of the component, because they close over nothing in it.
 *
 * Defined inside, they were rebuilt on every render, so the effects and callbacks that use them
 * could not honestly list them: naming an unstable function in a dependency array re-runs the
 * hook forever. At module scope they are simply stable, and the arrays below shrink instead of
 * needing an exception.
 */
function toFormShape(rule: TurIntegrationIndexingRule): IndexingRuleFormShape {
  return {
    ...rule,
    values: (rule.values ?? []).map((v) =>
      typeof v === "string" ? { value: v } : (v as { value: string })
    ),
  };
}

function fromFormShape(data: IndexingRuleFormShape): TurIntegrationIndexingRule {
  return {
    ...data,
    values: (data.values ?? [])
      .map((v) => v?.value)
      .filter((v): v is string => typeof v === "string"),
  };
}

export const IntegrationIndexingRulesForm: React.FC<IntegrationIndexingRulesFormProps> = ({
  value,
  integrationId,
  isNew,
  onDelete,
  open,
  setOpen,
}) => {
  const { t } = useTranslation();
  // Services - memoized to prevent recreation on each render
  const turSNSiteService = useMemo(() => new TurSNSiteService(), []);
  const turSNFieldService = useMemo(() => new TurSNFieldService(), []);
  const turIntegrationIndexingRuleService = useMemo(
    () => new TurIntegrationIndexingRuleService(integrationId),
    [integrationId]
  );
  const aemSourceService = useMemo(
    () => new TurIntegrationAemSourceService(integrationId),
    [integrationId]
  );
  const dbSourceService = useMemo(
    () => new TurIntegrationDbSourceService(integrationId),
    [integrationId]
  );
  const wcSourceService = useMemo(
    () => new TurIntegrationWcSourceService(integrationId),
    [integrationId]
  );
  const assetsSourceService = useMemo(
    () => new TurIntegrationAssetsSourceService(integrationId),
    [integrationId]
  );

  type ConnectorSourceOption = {
    key: string;
    label: string;
    type: string;
    snSiteNames: string[];
  };

  // Form setup with default values
  const form = useForm<IndexingRuleFormShape>({
    defaultValues: toFormShape(value)
  });
  const { control, register, setValue, watch } = form;

  // State
  const [turSNSites, setTurSNSites] = useState<TurSNSite[]>([]);
  const [connectorSources, setConnectorSources] = useState<ConnectorSourceOption[]>([]);
  const [turSNSiteFields, setTurSNSiteFields] = useState<TurSNSiteField[]>([]);
  const [isLoadingFields, setIsLoadingFields] = useState(false);

  // Ref to track pending attribute value that needs to be set after fields load
  const pendingAttributeRef = useRef<string | null>(value.attribute || null);

  // Navigation
  const navigate = useNavigate();
  // Watch selected source for dependent field loading
  const selectedSource = watch("source");

  // Find selected connector source - memoized for performance
  const selectedConnectorSource = useMemo(
    () => connectorSources.find((s) => s.key === selectedSource),
    [connectorSources, selectedSource]
  );

  // Resolve the SN site whose fields will populate the attribute dropdown
  const selectedSite = useMemo(() => {
    const firstSnSiteName = selectedConnectorSource?.snSiteNames?.[0];
    if (!firstSnSiteName) return undefined;
    return turSNSites.find((site) => site.name === firstSnSiteName);
  }, [turSNSites, selectedConnectorSource]);

  // Load initial data: SN sites (for field lookup) + connector sources (AEM/DB/WC/Assets)
  useEffect(() => {
    const loadSites = async () => {
      try {
        const sites = await turSNSiteService.query();
        setTurSNSites(sites);
      } catch (error) {
        console.error("Failed to load sites:", error);
        toast.error(t("forms.integrationRules.siteLoadFailed"));
      }
    };
    loadSites();
  }, [turSNSiteService, t]);

  useEffect(() => {
    const loadSources = async () => {
      const [aem, db, wc, assets] = await Promise.allSettled([
        aemSourceService.query(),
        dbSourceService.query(),
        wcSourceService.query(),
        assetsSourceService.query(),
      ]);
      const items: ConnectorSourceOption[] = [];
      if (aem.status === "fulfilled") {
        aem.value.forEach((s) =>
          items.push({
            key: s.name,
            label: s.name,
            type: "AEM",
            snSiteNames: [s.authorSNSite, s.publishSNSite].filter(
              (n): n is string => !!n
            ),
          })
        );
      }
      if (db.status === "fulfilled") {
        db.value.forEach((s) =>
          items.push({
            key: s.name,
            label: s.name,
            type: "DB",
            snSiteNames: s.turSNSites ?? [],
          })
        );
      }
      if (wc.status === "fulfilled") {
        wc.value.forEach((s) =>
          items.push({
            key: s.id,
            label: s.title,
            type: "Web Crawler",
            snSiteNames: s.turSNSites ?? [],
          })
        );
      }
      if (assets.status === "fulfilled") {
        assets.value.forEach((s) =>
          items.push({
            key: s.name,
            label: s.name,
            type: "Assets",
            snSiteNames: s.turSNSites ?? [],
          })
        );
      }
      if ([aem, db, wc, assets].every((r) => r.status === "rejected")) {
        toast.error(t("forms.integrationRules.sourceLoadFailed"));
      }
      setConnectorSources(items);
    };
    loadSources();
  }, [aemSourceService, dbSourceService, wcSourceService, assetsSourceService, t]);

  // Reset form when value changes
  useEffect(() => {
    form.reset(toFormShape(value));
    // Store the attribute to be set after fields are loaded
    pendingAttributeRef.current = value.attribute || null;
  }, [form, value]);

  // Load fields when site selection changes
  useEffect(() => {
    const loadFields = async () => {
      if (!selectedSite?.id) {
        setTurSNSiteFields([]);
        return;
      }

      setIsLoadingFields(true);
      try {
        const fields = await turSNFieldService.query(selectedSite.id);
        setTurSNSiteFields(fields);
      } catch (error) {
        console.error("Failed to load fields:", error);
        toast.error(t("forms.integrationRules.fieldLoadFailed"));
      } finally {
        setIsLoadingFields(false);
      }
    };

    loadFields();
  }, [selectedSite?.id, turSNFieldService, t]);

  // Set attribute value after fields are loaded
  useEffect(() => {
    if (turSNSiteFields.length > 0 && pendingAttributeRef.current) {
      const attributeExists = turSNSiteFields.some(
        (field) => field.name === pendingAttributeRef.current
      );
      if (attributeExists) {
        setValue("attribute", pendingAttributeRef.current);
      }
      pendingAttributeRef.current = null;
    }
  }, [turSNSiteFields, setValue]);

  // Form submission handler - memoized with useCallback
  const onSubmit = useCallback(async (data: IndexingRuleFormShape) => {
    const payload = fromFormShape(data);
    try {
      if (isNew) {
        const result = await turIntegrationIndexingRuleService.create(payload);
        if (result) {
          form.reset(data); // Reset dirty state after successful save
          toast.success(t("forms.integrationRules.created", { name: payload.name }));
          navigate(`${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/indexing-rule`);
        } else {
          toast.error(t("forms.integrationRules.createFailed"));
        }
      } else {
        const result = await turIntegrationIndexingRuleService.update(payload);
        if (result) {
          form.reset(data); // Reset dirty state after successful save
          toast.success(t("forms.integrationRules.updated", { name: payload.name }));
        } else {
          toast.error(t("forms.integrationRules.updateFailed"));
        }
      }
    } catch (error) {
      console.error("Form submission error:", error);
      toast.error(t("forms.integrationRules.saveFailed"));
    }
  }, [isNew, turIntegrationIndexingRuleService, navigate, form, integrationId, t]);

  // Check if attribute field should be disabled
  const isAttributeFieldDisabled = !selectedSource || isLoadingFields;

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
        <StickyPageHeader>
          <StickyPageHeader.Title
            icon={IconTools}
            feature={t("integration.indexingRules.title")}
            description={t("integration.indexingRules.description")}
          />
          <StickyPageHeader.Actions>
            {onDelete && open !== undefined && setOpen && (
              <DialogDelete feature={t("integration.indexingRules.title")} name={form.watch("name") ?? ""} onDelete={onDelete} open={open} setOpen={setOpen} />
            )}
            <GradientButton type="submit" size="sm">
              <IconDeviceFloppy className="size-4" />
              {t("forms.formActions.saveChanges")}
            </GradientButton>
            <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(`${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/indexing-rule`)}>
              <IconX className="size-4" />
              {t("forms.formActions.cancel")}
            </GradientButton>
          </StickyPageHeader.Actions>
        </StickyPageHeader>
        <div className="space-y-4">
            {/* Section: Basic Info */}
            <SectionCard variant="blue">
              <SectionCard.Header icon={IconFileText} title={t("forms.integrationRules.ruleDetails")} description={t("forms.integrationRules.ruleDetailsDesc")} />
              <SectionCard.Content>
                {/* Rule Name */}
                <FormField
                  control={control}
                  name="name"
                  rules={{ required: t("forms.integrationRules.ruleNameRequired") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.integrationRules.ruleName")}</FormLabel>
                      <FormDescription>
                        {t("forms.integrationRules.ruleNameDesc")}
                      </FormDescription>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder={t("forms.integrationRules.ruleNamePlaceholder")}
                          type="text"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                {/* Rule Description */}
                <FormField
                  control={control}
                  name="description"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.integrationRules.ruleDescLabel")}</FormLabel>
                      <FormDescription>
                        {t("forms.integrationRules.ruleDescDesc")}
                      </FormDescription>
                      <FormControl>
                        <Textarea
                          {...field}
                          placeholder={t("forms.integrationRules.ruleDescPlaceholder")}
                          className="resize-none"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>

            {/* Section: Site Settings */}
            <SectionCard variant="violet">
              <SectionCard.Header icon={IconMap} title={t("forms.integrationRules.whereApply")} description={t("forms.integrationRules.whereApplyDesc")} />
              <SectionCard.Content>
                {/* Connector Source */}
                <FormField
                  control={control}
                  name="source"
                  rules={{ required: t("forms.integrationRules.sourceRequired") }}
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.integrationRules.chooseSource")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.integrationRules.chooseSourceDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value}>
                            <SelectTrigger className="w-full">
                              <SelectValue placeholder={t("forms.integrationRules.selectSource")} />
                            </SelectTrigger>
                            <SelectContent>
                              {connectorSources.map((source) => (
                                <SelectItem key={`${source.type}:${source.key}`} value={source.key}>
                                  {source.label} ({source.type})
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </FormControl>
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />

                {/* Target Attribute/Field */}
                <FormField
                  control={control}
                  name="attribute"
                  rules={{ required: t("forms.integrationRules.fieldRequired") }}
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.integrationRules.pickField")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.integrationRules.pickFieldDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select
                            onValueChange={field.onChange}
                            value={field.value}
                            disabled={isAttributeFieldDisabled}
                          >
                            <SelectTrigger className="w-full">
                              <SelectValue
                                placeholder={isLoadingFields ? t("forms.integrationRules.loadingFields") : t("forms.integrationRules.selectField")}
                              />
                            </SelectTrigger>
                            <SelectContent>
                              {turSNSiteFields.map((siteField) => (
                                <SelectItem key={siteField.id} value={siteField.name}>
                                  {siteField.name}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </FormControl>
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>

            {/* Section: Rule Action */}
            <SectionCard variant="emerald">
              <SectionCard.Header icon={IconGavel} title={t("forms.integrationRules.whatHappens")} description={t("forms.integrationRules.whatHappensDesc")} />
              <SectionCard.Content>
                {/* Action Type */}
                <FormField
                  control={control}
                  name="ruleType"
                  rules={{ required: t("forms.integrationRules.actionRequired") }}
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.integrationRules.actionLabel")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.integrationRules.actionDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value}>
                            <SelectTrigger className="w-full">
                              <SelectValue placeholder={t("forms.integrationRules.chooseAction")} />
                            </SelectTrigger>
                            <SelectContent>
                              {RULE_TYPES.map((ruleType: typeof RULE_TYPES[number]) => (
                                <SelectItem key={ruleType.value} value={ruleType.value}>
                                  {ruleType.label}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </FormControl>
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>

            {/* Section: Matching Values */}
            <SectionCard variant="amber">
              <SectionCard.Header icon={IconList} title={t("forms.integrationRules.whichValues")} description={t("forms.integrationRules.whichValuesDesc")} />
              <SectionCard.Content>
                <FormItem>
                  <FormLabel>{t("forms.integrationRules.valuesToMatch")}</FormLabel>
                  <FormDescription>
                    {t("forms.integrationRules.valuesToMatchDesc")}
                  </FormDescription>
                  <FormControl>
                    <DynamicIndexingRuleFields
                      fieldName="values"
                      control={control}
                      register={register}
                    />
                  </FormControl>
                </FormItem>
              </SectionCard.Content>
            </SectionCard>
          </div>

          {/* Action Footer */}
      </form>
    </Form>
  );
}

