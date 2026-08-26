"use client"

import { toast } from "@viglet/viglet-design-system"
import { StickyPageHeader } from "@viglet/viglet-design-system/router"
import { useCallback, useEffect, useMemo, useState } from "react"
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
import { useConnectorProvider } from "@/hooks/use-connector-provider"
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model"
import type { TurIntegrationAssetsSource } from "@/models/integration/integration-assets-source.model"
import type { TurIntegrationDbSource } from "@/models/integration/integration-db-source.model"
import type { TurIntegrationIndexingRule } from "@/models/integration/integration-indexing-rule.model"
import type { TurIntegrationWcSource } from "@/models/integration/integration-wc-source.model"
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service"
import { TurIntegrationAssetsSourceService } from "@/services/integration/integration-assets-source.service"
import { TurIntegrationDbSourceService } from "@/services/integration/integration-db-source.service"
import { TurIntegrationIndexingRuleService } from "@/services/integration/integration-indexing-rule.service"
import { TurIntegrationTuringSitesService, type TuringSNSite } from "@/services/integration/integration-turing-sites.service"
import { TurIntegrationWcSourceService } from "@/services/integration/integration-wc-source.service"
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

/** Connector provider names reported by `/v2/connector/status`. */
const PROVIDER_AEM = "AEM";
const PROVIDER_DB = "JDBC-DATABASE";
const PROVIDER_WC = "WEB-CRAWLER";
const PROVIDER_ASSETS = "ASSETS";

type ConnectorSourceOption = {
  /** Value persisted in the rule's `source` column. */
  key: string;
  /** Source primary key, used to fetch the full source when needed. */
  id: string;
  label: string;
  type: string;
  /** Turing SN site names the source indexes into. */
  snSiteNames: string[];
  /**
   * Attribute names the source itself declares. The rule is evaluated against
   * the job item attributes (see `IgnoreIndexingRuleStrategy`), so these are the
   * authoritative options — SN site fields are only a complement.
   */
  attributeNames: string[];
};

const notBlank = (value: string | undefined | null): value is string => !!value;

const toAemOption = (source: TurIntegrationAemSource): ConnectorSourceOption => ({
  key: source.name,
  id: source.id,
  label: source.name,
  type: "AEM",
  snSiteNames: [source.authorSNSite, source.publishSNSite].filter(notBlank),
  attributeNames: [
    ...(source.attributeSpecifications ?? []).map((spec) => spec.name),
    ...(source.models ?? []).flatMap((model) =>
      (model.targetAttrs ?? []).map((attr) => attr.name)
    ),
  ].filter(notBlank),
});

const toDbOption = (source: TurIntegrationDbSource): ConnectorSourceOption => ({
  key: source.name,
  id: source.id,
  label: source.name,
  type: "DB",
  snSiteNames: source.turSNSites ?? [],
  // Attributes come from the SQL query columns, so they are only known after a
  // run — fall back to the SN site fields.
  attributeNames: [],
});

const toWcOption = (source: TurIntegrationWcSource): ConnectorSourceOption => ({
  key: source.id,
  id: source.id,
  label: source.title,
  type: "Web Crawler",
  snSiteNames: source.turSNSites ?? [],
  attributeNames: (source.attributeMappings ?? [])
    .map((attr) => attr.name)
    .filter(notBlank),
});

const toAssetsOption = (source: TurIntegrationAssetsSource): ConnectorSourceOption => ({
  key: source.name,
  id: source.id,
  label: source.name,
  type: "Assets",
  snSiteNames: source.turSNSites ?? [],
  attributeNames: [],
});

// Props interface with descriptive naming
interface IntegrationIndexingRulesFormProps {
  value: TurIntegrationIndexingRule;
  integrationId: string;
  isNew: boolean;
  onDelete?: () => void;
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
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
  const { provider, loading: isLoadingProvider } = useConnectorProvider(integrationId);
  // Services - memoized to prevent recreation on each render
  const turingSitesService = useMemo(
    () => new TurIntegrationTuringSitesService(integrationId),
    [integrationId]
  );
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

  // Internal form shape: values wrapped as { value: string } so useFieldArray can manage them
  type IndexingRuleFormShape = Omit<TurIntegrationIndexingRule, "values"> & {
    values: { value: string }[];
  };

  // Every text/select field is coerced to a string so the inputs are controlled
  // from the first render — a Radix Select that starts with `value={undefined}`
  // would switch from uncontrolled to controlled when the rule arrives.
  const toFormShape = (rule: TurIntegrationIndexingRule): IndexingRuleFormShape => ({
    ...rule,
    name: rule.name ?? "",
    description: rule.description ?? "",
    source: rule.source ?? "",
    attribute: rule.attribute ?? "",
    ruleType: rule.ruleType ?? "",
    values: (rule.values ?? []).map((v) =>
      typeof v === "string" ? { value: v } : (v as { value: string })
    ),
  });

  const fromFormShape = (data: IndexingRuleFormShape): TurIntegrationIndexingRule => ({
    ...data,
    values: (data.values ?? [])
      .map((v) => v?.value)
      .filter((v): v is string => typeof v === "string"),
  });

  // Form setup with default values
  const form = useForm<IndexingRuleFormShape>({
    defaultValues: toFormShape(value)
  });
  const { control, register, watch } = form;

  // State
  const [turingSites, setTuringSites] = useState<TuringSNSite[]>([]);
  const [connectorSources, setConnectorSources] = useState<ConnectorSourceOption[]>([]);
  const [snSiteFieldNames, setSnSiteFieldNames] = useState<string[]>([]);
  const [detailAttributeNames, setDetailAttributeNames] = useState<string[]>([]);
  const [isLoadingFields, setIsLoadingFields] = useState(false);

  // Navigation
  const navigate = useNavigate();
  // Watch the form values: they drive the dependent lookups and must stay
  // selectable even before (or without) the option lists being resolved.
  const sourceValue = watch("source");
  const attributeValue = watch("attribute");

  /*
   * The rule's own values, taken straight from the props. `form.reset` only runs
   * in an effect, so these are known one render EARLIER than the form values —
   * and an option must exist no later than the commit that sets the value:
   * Radix renders a hidden native <select> for form participation, and when it
   * receives a value with no matching <option> it falls back to "", dispatches a
   * change event and writes that "" back through onValueChange. That silently
   * wipes the persisted source/attribute (ruleType escapes it because its
   * options are static). Seeding the option lists from the props keeps the
   * <option> ahead of the value.
   */
  const persistedSource = value.source;
  const persistedAttribute = value.attribute;

  // The source in effect: the form value once reset ran, the rule's own before.
  const selectedSourceKey = sourceValue || persistedSource;

  // Find selected connector source - memoized for performance
  const selectedConnectorSource = useMemo(
    () => connectorSources.find((s) => s.key === selectedSourceKey),
    [connectorSources, selectedSourceKey]
  );

  // Resolve the SN site whose fields complement the attribute dropdown
  const selectedSiteId = useMemo(() => {
    const firstSnSiteName = selectedConnectorSource?.snSiteNames?.[0];
    if (!firstSnSiteName) return undefined;
    return turingSites.find((site) => site.name === firstSnSiteName)?.id;
  }, [turingSites, selectedConnectorSource]);

  // Turing SN sites, read through the connector so it also works standalone
  useEffect(() => {
    let active = true;
    turingSitesService
      .query()
      .then((sites) => {
        if (active) setTuringSites(sites);
      })
      .catch((error) => {
        // Non-fatal: the field options fall back to the source's own attributes.
        console.warn("Failed to load Turing SN sites:", error);
      });
    return () => {
      active = false;
    };
  }, [turingSitesService]);

  // Connector sources of the *active* provider only. A Dumont instance runs a
  // single plugin, so querying the others would hit endpoints that do not exist.
  useEffect(() => {
    if (isLoadingProvider) return;

    let active = true;
    const loadSources = async (): Promise<ConnectorSourceOption[]> => {
      switch (provider) {
        case PROVIDER_AEM:
          return (await aemSourceService.query()).map(toAemOption);
        case PROVIDER_DB:
          return (await dbSourceService.query()).map(toDbOption);
        case PROVIDER_WC:
          return (await wcSourceService.query()).map(toWcOption);
        case PROVIDER_ASSETS:
          return (await assetsSourceService.query()).map(toAssetsOption);
        default:
          // Provider detection failed; try every plugin so the form still works.
          return (
            await Promise.allSettled([
              aemSourceService.query().then((s) => s.map(toAemOption)),
              dbSourceService.query().then((s) => s.map(toDbOption)),
              wcSourceService.query().then((s) => s.map(toWcOption)),
              assetsSourceService.query().then((s) => s.map(toAssetsOption)),
            ])
          )
            .filter((r) => r.status === "fulfilled")
            .flatMap((r) => r.value);
      }
    };

    loadSources()
      .then((items) => {
        if (active) setConnectorSources(items);
      })
      .catch((error) => {
        console.error("Failed to load sources:", error);
        toast.error(t("forms.integrationRules.sourceLoadFailed"));
      });

    return () => {
      active = false;
    };
  }, [
    provider,
    isLoadingProvider,
    aemSourceService,
    dbSourceService,
    wcSourceService,
    assetsSourceService,
    t,
  ]);

  // Reset form when value changes
  useEffect(() => {
    form.reset(toFormShape(value));
  }, [form, value]);

  // Load SN site fields when the resolved site changes
  useEffect(() => {
    if (!selectedSiteId) {
      setSnSiteFieldNames([]);
      return;
    }

    let active = true;
    setIsLoadingFields(true);
    turingSitesService
      .fields(selectedSiteId)
      .then((fields) => {
        if (active) setSnSiteFieldNames(fields.map((field) => field.name).filter(notBlank));
      })
      .catch((error) => {
        console.error("Failed to load fields:", error);
        toast.error(t("forms.integrationRules.fieldLoadFailed"));
      })
      .finally(() => {
        if (active) setIsLoadingFields(false);
      });

    return () => {
      active = false;
    };
  }, [selectedSiteId, turingSitesService, t]);

  // The AEM list endpoint may omit the LAZY attributeSpecifications collection;
  // when that happens, read them from the source detail endpoint, which always
  // loads them explicitly.
  useEffect(() => {
    const source = selectedConnectorSource;
    if (!source?.id || source.type !== "AEM" || source.attributeNames.length > 0) {
      setDetailAttributeNames([]);
      return;
    }

    let active = true;
    aemSourceService
      .get(source.id)
      .then((detail) => {
        if (active) setDetailAttributeNames(toAemOption(detail).attributeNames);
      })
      .catch((error) => {
        console.warn("Failed to load AEM source attributes:", error);
      });

    return () => {
      active = false;
    };
  }, [selectedConnectorSource, aemSourceService]);

  // Source options always include the value in effect: the rule must keep
  // showing its own source even if the source was renamed or is still loading.
  const sourceOptions = useMemo(() => {
    if (!selectedSourceKey || connectorSources.some((s) => s.key === selectedSourceKey)) {
      return connectorSources;
    }
    return [
      ...connectorSources,
      {
        key: selectedSourceKey,
        id: "",
        label: selectedSourceKey,
        type: "",
        snSiteNames: [],
        attributeNames: [],
      },
    ];
  }, [connectorSources, selectedSourceKey]);

  // Field options: attributes declared by the source, complemented by the SN
  // site fields, plus the rule's own attribute so editing never drops it.
  const fieldOptions = useMemo(() => {
    const names = new Set<string>([
      ...(selectedConnectorSource?.attributeNames ?? []),
      ...detailAttributeNames,
      ...snSiteFieldNames,
    ]);
    if (persistedAttribute) names.add(persistedAttribute);
    if (attributeValue) names.add(attributeValue);
    return [...names].filter(notBlank).sort((a, b) => a.localeCompare(b));
  }, [
    selectedConnectorSource,
    detailAttributeNames,
    snSiteFieldNames,
    persistedAttribute,
    attributeValue,
  ]);

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
  }, [isNew, turIntegrationIndexingRuleService, navigate, form]);

  // Keep the dropdown usable as soon as there is something to pick: only block
  // it while the first batch of options is still on its way.
  const isAttributeFieldDisabled =
    !selectedSourceKey || (isLoadingFields && fieldOptions.length === 0);

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
                              {sourceOptions.map((source) => (
                                <SelectItem key={`${source.type}:${source.key}`} value={source.key}>
                                  {source.type ? `${source.label} (${source.type})` : source.label}
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
                              {fieldOptions.map((fieldName) => (
                                <SelectItem key={fieldName} value={fieldName}>
                                  {fieldName}
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

