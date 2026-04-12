import { ROUTES } from "@/app/routes.const";
import { SubPageHeader } from "@/components/sub.page.header";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { StickySaveBar } from "@/components/ui/sticky-save-bar";
import { Input } from "@/components/ui/input";
import { SectionCard } from "@/components/ui/section-card";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";

import { TargetAttributeList } from "@/components/integration/target.attribute.list";
import type { TurIntegrationAemPluginModel } from "@/models/integration/integration-aem-plugin-model.model";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { IconBox, IconSettings, IconTarget } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

const emptyModel: TurIntegrationAemPluginModel = {
  id: "",
  type: "",
  subType: "",
  className: "",
  targetAttrs: [],
};

export default function IntegrationInstanceSourceModelPage() {
  const navigate = useNavigate();
  const { id, sourceId, modelIndex } = useParams() as {
    id: string;
    sourceId: string;
    modelIndex: string;
  };
  const { t } = useTranslation();
  const turIntegrationAemSourceService = useMemo(() => new TurIntegrationAemSourceService(id), [id]);
  const [source, setSource] = useState<TurIntegrationAemSource | null>(null);
  const isNew = modelIndex === "new";
  const index = isNew ? -1 : parseInt(modelIndex, 10);

  const modelTabRoute = `${ROUTES.INTEGRATION_INSTANCE}/${id}/source/${sourceId}/models`;

  const form = useForm<TurIntegrationAemPluginModel>({
    defaultValues: emptyModel,
  });

  useEffect(() => {
    turIntegrationAemSourceService.get(sourceId).then((s) => {
      setSource(s);
      const models = Array.isArray(s.models) ? s.models : [];
      if (!isNew && index >= 0 && index < models.length) {
        form.reset(models[index]);
      } else if (isNew) {
        form.reset(emptyModel);
      }
    });
  }, [id, sourceId, modelIndex]);

  async function onSubmit(model: TurIntegrationAemPluginModel) {
    if (!source) return;
    try {
      const models = Array.isArray(source.models) ? [...source.models] : [];
      if (isNew) {
        models.push(model);
      } else {
        models[index] = model;
      }
      const updated = { ...source, models };
      const result = await turIntegrationAemSourceService.update(updated);
      if (result) {
        toast.success(t("forms.common.updated", { name: model.type, feature: t("forms.integrationSource.models") }));
        navigate(modelTabRoute);
      } else {
        toast.error(t("forms.common.formSubmitFailed"));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <>
      <SubPageHeader
        icon={IconBox}
        feature={t("forms.integrationSource.models")}
        name={form.watch("type") || t("forms.integrationSource.modelUntitled")}
        description={t("forms.integrationSource.modelDetailDesc")}
      />

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-6">
          <StickySaveBar
            title={form.watch("type") || t("forms.integrationSource.modelUntitled")}
            onCancel={() => navigate(modelTabRoute)}
          />
          <SectionCard variant="violet">
            <SectionCard.Header
              icon={IconSettings}
              title={t("forms.integrationSource.modelDetail")}
              description={t("forms.integrationSource.modelDetailDesc")}
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="type"
                rules={{ required: t("forms.integrationSource.modelTypeRequired") }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.integrationSource.modelType")}</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="cq:Page" />
                    </FormControl>
                    <FormDescription>{t("forms.integrationSource.modelTypeDesc")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="subType"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.integrationSource.modelSubType")}</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="cq:PageContent" />
                    </FormControl>
                    <FormDescription>{t("forms.integrationSource.modelSubTypeDesc")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="className"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.integrationSource.modelClassName")}</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="com.example.ModelHandler" />
                    </FormControl>
                    <FormDescription>{t("forms.integrationSource.modelClassNameDesc")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          <SectionCard variant="blue">
            <SectionCard.Header
              icon={IconTarget}
              title={t("forms.integrationSource.targetAttrs")}
              description={t("forms.integrationSource.targetAttrsDesc")}
            />
            <SectionCard.Content>
              <TargetAttributeList modelFieldPrefix="targetAttrs" />
            </SectionCard.Content>
          </SectionCard>

        </form>
      </Form>
    </>
  );
}
