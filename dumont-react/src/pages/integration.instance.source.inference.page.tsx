import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import { SectionCard } from "@/components/ui/section-card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { SubPageHeader } from "@/components/sub.page.header";
import {
  FIELD_TYPES,
  type DumInferredField,
  type DumSourceDraft,
  type TurSEFieldType,
} from "@/models/integration/integration-source-inference.model";
import { TurIntegrationSourceInferenceService } from "@/services/integration/integration-source-inference.service";
import { IconPlus, IconTrash, IconWand } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const SHAPE_VARIANT: Record<string, "blue" | "violet" | "emerald" | "slate"> = {
  TABULAR_LISTING: "emerald",
  HTML_LISTING: "blue",
  SITEMAP: "violet",
  UNKNOWN: "slate",
};

export default function IntegrationInstanceSourceInferencePage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const service = useMemo(() => new TurIntegrationSourceInferenceService(id), [id]);

  const [url, setUrl] = useState("");
  const [inferring, setInferring] = useState(false);
  const [indexing, setIndexing] = useState(false);
  const [draft, setDraft] = useState<DumSourceDraft | null>(null);
  const [sourceName, setSourceName] = useState("");
  const [siteNames, setSiteNames] = useState("");
  const [locale, setLocale] = useState("en-US");

  const isTabular = draft?.shape === "TABULAR_LISTING";

  async function onInfer() {
    if (!url.trim()) return;
    setInferring(true);
    try {
      const result = await service.infer(url.trim());
      setDraft(result);
      if (result.shape === "UNKNOWN") {
        toast.warning(t("integration.sourceInference.unknownShape"));
      } else {
        toast.success(t("integration.sourceInference.inferred", { shape: result.shape }));
      }
    } catch (error) {
      console.error("Inference error", error);
      toast.error(t("integration.sourceInference.inferFailed"));
    } finally {
      setInferring(false);
    }
  }

  function patchConfig(key: string, value: string) {
    setDraft((d) => (d ? { ...d, strategyConfig: { ...d.strategyConfig, [key]: value } } : d));
  }

  function patchField(index: number, patch: Partial<DumInferredField>) {
    setDraft((d) =>
      d ? { ...d, fields: d.fields.map((f, i) => (i === index ? { ...f, ...patch } : f)) } : d
    );
  }

  function addField() {
    setDraft((d) =>
      d
        ? {
            ...d,
            fields: [
              ...d.fields,
              { name: "", type: "STRING", multiValued: false, facet: false, mandatory: false },
            ],
          }
        : d
    );
  }

  function removeField(index: number) {
    setDraft((d) => (d ? { ...d, fields: d.fields.filter((_, i) => i !== index) } : d));
  }

  async function onConfirmIndex() {
    if (!draft || !sourceName.trim()) {
      toast.error(t("integration.sourceInference.sourceNameRequired"));
      return;
    }
    setIndexing(true);
    try {
      const result = await service.index({
        url: url.trim(),
        sourceName: sourceName.trim(),
        siteNames: siteNames.split(",").map((s) => s.trim()).filter(Boolean),
        locale: locale.trim() || undefined,
        draft,
      });
      if (result.success) {
        toast.success(t("integration.sourceInference.indexed", { source: result.source }));
      } else {
        toast.error(result.message ?? t("integration.sourceInference.indexFailed"));
      }
    } catch (error) {
      console.error("Index error", error);
      toast.error(t("integration.sourceInference.indexFailed"));
    } finally {
      setIndexing(false);
    }
  }

  return (
    <>
      <SubPageHeader
        icon={IconWand}
        name={t("integration.sourceInference.title")}
        feature={t("integration.sourceInference.title")}
        description={t("integration.sourceInference.description")}
      />

      <div className="space-y-4">
        {/* Step 1 — point at a URL */}
        <SectionCard variant="blue">
          <SectionCard.StaticHeader
            icon={IconWand}
            title={t("integration.sourceInference.step1Title")}
            description={t("integration.sourceInference.step1Desc")}
          />
          <SectionCard.Content>
            <div className="flex flex-col sm:flex-row gap-2 items-stretch sm:items-end">
              <div className="flex-1">
                <label className="text-sm text-muted-foreground" htmlFor="infer-url">
                  {t("integration.sourceInference.urlLabel")}
                </label>
                <Input
                  id="infer-url"
                  value={url}
                  placeholder="https://example.edu/cursos"
                  onChange={(e) => setUrl(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") onInfer();
                  }}
                />
              </div>
              <GradientButton type="button" onClick={onInfer} loading={inferring} disabled={!url.trim()}>
                <IconWand className="size-4 mr-1" />
                {t("integration.sourceInference.infer")}
              </GradientButton>
            </div>
          </SectionCard.Content>
        </SectionCard>

        {/* Step 2 — review the draft */}
        {draft && (
          <SectionCard variant={SHAPE_VARIANT[draft.shape] ?? "slate"}>
            <SectionCard.StaticHeader
              icon={IconWand}
              title={t("integration.sourceInference.step2Title")}
              description={t("integration.sourceInference.step2Desc")}
            />
            <SectionCard.Content>
              <div className="flex items-center gap-3 flex-wrap py-2 border-b">
                <Badge>{draft.shape}</Badge>
                <span className="text-sm text-muted-foreground">
                  {t("integration.sourceInference.confidence")}:{" "}
                  {Math.round((draft.confidence ?? 0) * 100)}%
                </span>
                {draft.notes && (
                  <span className="text-sm italic text-muted-foreground">{draft.notes}</span>
                )}
              </div>

              {draft.shape === "UNKNOWN" ? (
                <p className="text-sm text-muted-foreground py-4">
                  {t("integration.sourceInference.unknownHelp")}
                </p>
              ) : (
                <>
                  {!isTabular && (
                    <p className="text-sm text-amber-600 dark:text-amber-400 py-2">
                      {t("integration.sourceInference.notMaterializable", { shape: draft.shape })}
                    </p>
                  )}

                  {/* Target */}
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 py-3">
                    <div>
                      <label className="text-sm text-muted-foreground" htmlFor="src-name">
                        {t("integration.sourceInference.sourceName")}
                      </label>
                      <Input id="src-name" value={sourceName} onChange={(e) => setSourceName(e.target.value)} />
                    </div>
                    <div>
                      <label className="text-sm text-muted-foreground" htmlFor="src-sites">
                        {t("integration.sourceInference.sites")}
                      </label>
                      <Input id="src-sites" value={siteNames} placeholder="Sample" onChange={(e) => setSiteNames(e.target.value)} />
                    </div>
                    <div>
                      <label className="text-sm text-muted-foreground" htmlFor="src-locale">
                        {t("integration.sourceInference.locale")}
                      </label>
                      <Input id="src-locale" value={locale} onChange={(e) => setLocale(e.target.value)} />
                    </div>
                  </div>

                  {/* Strategy config (tabular) */}
                  {isTabular && (
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 py-2 border-t">
                      <div>
                        <label className="text-sm text-muted-foreground" htmlFor="cfg-row">
                          {t("integration.sourceInference.rowSelector")}
                        </label>
                        <Input
                          id="cfg-row"
                          value={draft.strategyConfig.rowSelector ?? ""}
                          onChange={(e) => patchConfig("rowSelector", e.target.value)}
                        />
                      </div>
                      <div>
                        <label className="text-sm text-muted-foreground" htmlFor="cfg-id">
                          {t("integration.sourceInference.idField")}
                        </label>
                        <Input
                          id="cfg-id"
                          value={draft.strategyConfig.idField ?? ""}
                          onChange={(e) => patchConfig("idField", e.target.value)}
                        />
                      </div>
                    </div>
                  )}

                  {/* Field manifest */}
                  <div className="py-3 border-t">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm font-medium">
                        {t("integration.sourceInference.fieldManifest")}
                      </span>
                      <GradientButton type="button" variant="outline" size="sm" onClick={addField}>
                        <IconPlus className="size-4 mr-1" />
                        {t("integration.sourceInference.addField")}
                      </GradientButton>
                    </div>
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>{t("integration.sourceInference.colName")}</TableHead>
                          <TableHead>{t("integration.sourceInference.colType")}</TableHead>
                          <TableHead className="text-center">{t("integration.sourceInference.colMulti")}</TableHead>
                          <TableHead className="text-center">{t("integration.sourceInference.colFacet")}</TableHead>
                          <TableHead className="text-center">{t("integration.sourceInference.colMandatory")}</TableHead>
                          <TableHead>{isTabular ? t("integration.sourceInference.colColumn") : t("integration.sourceInference.colSelector")}</TableHead>
                          <TableHead>{t("integration.sourceInference.colAttribute")}</TableHead>
                          <TableHead />
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {draft.fields.map((field, index) => (
                          <TableRow key={index}>
                            <TableCell>
                              <Input
                                value={field.name}
                                onChange={(e) => patchField(index, { name: e.target.value })}
                              />
                            </TableCell>
                            <TableCell>
                              <Select
                                value={field.type}
                                onValueChange={(v) => patchField(index, { type: v as TurSEFieldType })}
                              >
                                <SelectTrigger className="w-28">
                                  <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                  {FIELD_TYPES.map((ft) => (
                                    <SelectItem key={ft} value={ft}>
                                      {ft}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                            </TableCell>
                            <TableCell className="text-center">
                              <Checkbox
                                checked={field.multiValued}
                                onCheckedChange={(c) => patchField(index, { multiValued: c === true })}
                              />
                            </TableCell>
                            <TableCell className="text-center">
                              <Checkbox
                                checked={field.facet}
                                onCheckedChange={(c) => patchField(index, { facet: c === true })}
                              />
                            </TableCell>
                            <TableCell className="text-center">
                              <Checkbox
                                checked={field.mandatory}
                                onCheckedChange={(c) => patchField(index, { mandatory: c === true })}
                              />
                            </TableCell>
                            <TableCell>
                              {isTabular ? (
                                <Input
                                  type="number"
                                  className="w-20"
                                  value={field.columnIndex ?? ""}
                                  onChange={(e) =>
                                    patchField(index, {
                                      columnIndex: e.target.value === "" ? null : Number(e.target.value),
                                      selector: e.target.value === "" ? field.selector : null,
                                    })
                                  }
                                />
                              ) : (
                                <Input
                                  value={field.selector ?? ""}
                                  onChange={(e) => patchField(index, { selector: e.target.value || null })}
                                />
                              )}
                            </TableCell>
                            <TableCell>
                              <Input
                                value={field.attribute ?? ""}
                                placeholder="abs:href"
                                onChange={(e) => patchField(index, { attribute: e.target.value || null })}
                              />
                            </TableCell>
                            <TableCell>
                              <GradientButton
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                onClick={() => removeField(index)}
                              >
                                <IconTrash className="size-4" />
                              </GradientButton>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>

                  <div className="flex justify-end pt-3 border-t">
                    <GradientButton
                      type="button"
                      variant="success"
                      onClick={onConfirmIndex}
                      loading={indexing}
                      disabled={!isTabular || draft.fields.length === 0}
                    >
                      {t("integration.sourceInference.confirmIndex")}
                    </GradientButton>
                  </div>
                </>
              )}
            </SectionCard.Content>
          </SectionCard>
        )}
      </div>
    </>
  );
}
