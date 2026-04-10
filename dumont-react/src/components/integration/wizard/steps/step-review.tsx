import { Badge } from "@/components/ui/badge";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import {
  IconCheck,
  IconPlugConnected,
  IconServer,
  IconUser,
  IconWorld,
  IconLanguage,
  IconFileDescription,
  IconBox,
} from "@tabler/icons-react";
import type { UseFormReturn } from "react-hook-form";
import { useTranslation } from "react-i18next";

interface StepReviewProps {
  form: UseFormReturn<TurIntegrationAemSource>;
}

function ReviewSection({
  icon: Icon,
  title,
  children,
}: Readonly<{
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  children: React.ReactNode;
}>) {
  return (
    <div className="rounded-lg border p-4 space-y-2">
      <div className="flex items-center gap-2 text-sm font-semibold">
        <Icon className="size-4 text-primary" />
        {title}
      </div>
      <div className="grid gap-1.5 text-sm">{children}</div>
    </div>
  );
}

function ReviewRow({
  label,
  value,
  badge,
}: Readonly<{
  label: string;
  value?: string | number;
  badge?: boolean;
}>) {
  if (!value && value !== 0) return null;
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-muted-foreground">{label}</span>
      {badge ? (
        <Badge variant="outline">{value}</Badge>
      ) : (
        <span className="font-medium truncate max-w-[260px] text-right">
          {value}
        </span>
      )}
    </div>
  );
}

export function StepReview({ form }: Readonly<StepReviewProps>) {
  const { t } = useTranslation();
  const values = form.getValues();

  const specCount = values.attributeSpecifications?.length ?? 0;
  const modelCount = values.models?.length ?? 0;
  const localePathCount = values.localePaths?.length ?? 0;

  return (
    <div className="space-y-4 max-w-lg">
      <div>
        <h3 className="text-base font-semibold">
          {t("forms.wizard.stepReview")}
        </h3>
        <p className="text-sm text-muted-foreground mt-1">
          {t("forms.wizard.stepReviewDesc")}
        </p>
      </div>

      <ReviewSection icon={IconPlugConnected} title={t("forms.wizard.stepConnection")}>
        <ReviewRow label={t("forms.common.name")} value={values.name} />
        <ReviewRow
          label={t("forms.integrationSource.endpoint")}
          value={values.endpoint}
        />
        <ReviewRow
          label={t("forms.integrationSource.username")}
          value={values.username}
        />
      </ReviewSection>

      <ReviewSection icon={IconServer} title={t("forms.wizard.stepContent")}>
        <ReviewRow
          label={t("forms.integrationSource.contentType")}
          value={values.contentType}
          badge
        />
        {values.subType && (
          <ReviewRow
            label={t("forms.integrationSource.subType")}
            value={values.subType}
            badge
          />
        )}
        <ReviewRow
          label={t("forms.integrationSource.rootPath")}
          value={values.rootPath}
        />
        {values.oncePattern && (
          <ReviewRow
            label={t("forms.integrationSource.oncePattern")}
            value={values.oncePattern}
          />
        )}
      </ReviewSection>

      <ReviewSection icon={IconWorld} title={t("forms.wizard.stepSitesLocales")}>
        {values.author && (
          <>
            <div className="flex items-center gap-2">
              <IconUser className="size-3.5 text-violet-500" />
              <span className="text-xs font-medium">Author</span>
              <IconCheck className="size-3 text-emerald-500" />
            </div>
            <ReviewRow
              label={t("forms.integrationSource.authorSnSite")}
              value={values.authorSNSite}
            />
            {values.authorURLPrefix && (
              <ReviewRow
                label={t("forms.integrationSource.authorUrlPrefix")}
                value={values.authorURLPrefix}
              />
            )}
          </>
        )}
        {values.publish && (
          <>
            <div className="flex items-center gap-2">
              <IconWorld className="size-3.5 text-emerald-500" />
              <span className="text-xs font-medium">Publish</span>
              <IconCheck className="size-3 text-emerald-500" />
            </div>
            <ReviewRow
              label={t("forms.integrationSource.publishSnSite")}
              value={values.publishSNSite}
            />
            {values.publishURLPrefix && (
              <ReviewRow
                label={t("forms.integrationSource.publishUrlPrefix")}
                value={values.publishURLPrefix}
              />
            )}
          </>
        )}
        {!values.author && !values.publish && (
          <p className="text-xs text-muted-foreground italic">
            {t("forms.wizard.noSitesConfigured")}
          </p>
        )}
      </ReviewSection>

      <ReviewSection icon={IconLanguage} title={t("forms.integrationSource.localesConfig")}>
        <ReviewRow
          label={t("forms.integrationSource.defaultLocale")}
          value={values.defaultLocale}
          badge
        />
        {localePathCount > 0 && (
          <ReviewRow
            label={t("forms.integrationSource.localePaths")}
            value={localePathCount}
          />
        )}
      </ReviewSection>

      <ReviewSection icon={IconFileDescription} title={t("forms.integrationSource.specifications")}>
        <ReviewRow
          label={t("forms.wizard.fieldsCount")}
          value={specCount}
        />
        {specCount > 0 && (
          <div className="flex flex-wrap gap-1 mt-1">
            {values.attributeSpecifications.slice(0, 12).map((spec) => (
              <Badge key={spec.name} variant="outline" className="text-[10px]">
                {spec.name}
              </Badge>
            ))}
            {specCount > 12 && (
              <Badge variant="outline" className="text-[10px]">
                +{specCount - 12}
              </Badge>
            )}
          </div>
        )}
      </ReviewSection>

      <ReviewSection icon={IconBox} title={t("forms.integrationSource.models")}>
        <ReviewRow
          label={t("forms.wizard.modelsCount")}
          value={modelCount}
        />
        {values.models?.map((model) => (
          <div key={model.type + model.subType} className="flex items-center gap-2">
            <Badge variant="outline" className="text-[10px]">
              {model.type}
            </Badge>
            {model.subType && (
              <Badge variant="outline" className="text-[10px]">
                {model.subType}
              </Badge>
            )}
            <span className="text-xs text-muted-foreground">
              {model.targetAttrs?.length ?? 0} {t("forms.integrationSource.targetAttrs").toLowerCase()}
            </span>
          </div>
        ))}
      </ReviewSection>
    </div>
  );
}
