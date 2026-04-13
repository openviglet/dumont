import { ROUTES } from "@/app/routes.const";
import { Form } from "@/components/ui/form";
import { GradientButton } from "@/components/ui/gradient-button";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import {
  IconArrowLeft,
  IconArrowRight,
  IconFileDescription,
  IconBox,
  IconPlugConnected,
  IconRocket,
  IconServer,
  IconWorld,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { WizardStepper, type WizardStepperStep } from "@/components/ui/wizard-stepper";
import { StepConnection } from "./steps/step-connection";
import { StepContent } from "./steps/step-content";
import { StepFieldMapping } from "./steps/step-field-mapping";
import { StepReview } from "./steps/step-review";
import { StepSitesLocales } from "./steps/step-sites-locales";

interface AemSourceWizardProps {
  integrationId: string;
}

const STEP_DEFS = [
  { icon: IconPlugConnected, labelKey: "forms.wizard.stepConnection" },
  { icon: IconServer, labelKey: "forms.wizard.stepContent" },
  { icon: IconWorld, labelKey: "forms.wizard.stepSitesLocales" },
  { icon: IconFileDescription, labelKey: "forms.wizard.stepFieldMapping" },
  { icon: IconBox, labelKey: "forms.wizard.stepReview" },
];

export function AemSourceWizard({
  integrationId,
}: Readonly<AemSourceWizardProps>) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [currentStep, setCurrentStep] = useState(0);
  const [connectionTested, setConnectionTested] = useState(false);
  const [presetKey, setPresetKey] = useState("cq-page");
  const [creating, setCreating] = useState(false);

  const turIntegrationAemSourceService = useMemo(
    () => new TurIntegrationAemSourceService(integrationId),
    [integrationId],
  );

  const form = useForm<TurIntegrationAemSource>({
    defaultValues: {
      id: "",
      name: "",
      endpoint: "http://localhost:4502",
      username: "",
      password: "",
      rootPath: "",
      contentType: "cq:Page",
      subType: "cq:PageContent",
      oncePattern: "",
      defaultLocale: "",
      localeClass: "",
      deltaClass: "",
      author: false,
      publish: false,
      authorSNSite: "",
      publishSNSite: "",
      authorURLPrefix: "",
      publishURLPrefix: "",
      localePaths: [],
      attributeSpecifications: [],
      models: [],
    },
  });

  const watchName = form.watch("name");
  const watchEndpoint = form.watch("endpoint");
  const watchUsername = form.watch("username");
  const watchPassword = form.watch("password");
  const watchRootPath = form.watch("rootPath");
  const watchContentType = form.watch("contentType");
  const watchAuthor = form.watch("author");
  const watchPublish = form.watch("publish");
  const watchSpecs = form.watch("attributeSpecifications");

  const step1Done =
    !!watchEndpoint &&
    !!watchUsername &&
    !!watchPassword &&
    connectionTested;
  const step2Done = !!watchName && !!watchRootPath && !!watchContentType;
  const step3Done = watchAuthor || watchPublish;
  const step4Done = Array.isArray(watchSpecs) && watchSpecs.length > 0;

  const completedSteps = [step1Done, step2Done, step3Done, step4Done, false];
  const canGoNext = completedSteps[currentStep];

  const steps: WizardStepperStep[] = STEP_DEFS.map((s) => ({
    icon: s.icon,
    label: t(s.labelKey),
  }));

  const handleNext = () => {
    if (currentStep < STEP_DEFS.length - 1) {
      setCurrentStep((s) => s + 1);
    }
  };

  const handleBack = () => {
    if (currentStep > 0) {
      setCurrentStep((s) => s - 1);
    }
  };

  const sourceListRoute = `${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/source`;

  async function handleCreate() {
    setCreating(true);
    try {
      const data = form.getValues();
      // Strip empty ids so JPA generates new UUIDs (persist) instead of merge
      delete (data as any).id;
      data.localePaths?.forEach((lp: any) => delete lp.id);
      data.attributeSpecifications?.forEach((spec: any) => delete spec.id);
      data.models?.forEach((model: any) => {
        delete model.id;
        model.targetAttrs?.forEach((ta: any) => {
          delete ta.id;
          ta.sourceAttrs?.forEach((sa: any) => delete sa.id);
        });
      });
      const result = await turIntegrationAemSourceService.create(data);
      if (result) {
        toast.success(
          t("forms.common.saved", {
            name: data.name,
            feature: t("integration.sources.feature"),
          }),
        );
        navigate(sourceListRoute);
      } else {
        toast.error(t("forms.common.formSubmitFailed"));
      }
    } catch (error) {
      console.error("Wizard create error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    } finally {
      setCreating(false);
    }
  }

  return (
    <Form {...form}>
      <form
        onSubmit={(e) => e.preventDefault()}
        className="px-6"
        autoComplete="off"
      >
        <div className="mb-8">
          <WizardStepper
            steps={steps}
            currentStep={currentStep}
            completedSteps={completedSteps}
            onStepClick={setCurrentStep}
          />
        </div>

        {/* Step content */}
        <div className="min-h-[320px]">
          {currentStep === 0 && (
            <StepConnection
              form={form}
              integrationId={integrationId}
              onConnectionTested={setConnectionTested}
              connectionTested={connectionTested}
            />
          )}
          {currentStep === 1 && (
            <StepContent
              form={form}
              integrationId={integrationId}
              presetKey={presetKey}
              onPresetChange={setPresetKey}
            />
          )}
          {currentStep === 2 && <StepSitesLocales form={form} integrationId={integrationId} />}
          {currentStep === 3 && (
            <StepFieldMapping form={form} presetKey={presetKey} />
          )}
          {currentStep === 4 && <StepReview form={form} />}
        </div>

        {/* Navigation */}
        <div className="flex items-center justify-between pt-4 pb-4 border-t mt-8">
          <div className="flex items-center gap-2">
            {currentStep > 0 ? (
              <GradientButton
                type="button"
                variant="outline"
                onClick={handleBack}
              >
                <IconArrowLeft className="size-4" />
                {t("forms.wizard.back")}
              </GradientButton>
            ) : (
              <GradientButton
                type="button"
                variant="ghost"
                onClick={() => navigate(sourceListRoute)}
              >
                {t("forms.formActions.cancel")}
              </GradientButton>
            )}
          </div>

          <div>
            {currentStep < STEP_DEFS.length - 1 ? (
              <GradientButton
                type="button"
                onClick={handleNext}
                disabled={!canGoNext}
              >
                {t("forms.wizard.next")}
                <IconArrowRight className="size-4" />
              </GradientButton>
            ) : (
              <GradientButton
                type="button"
                onClick={handleCreate}
                loading={creating}
                disabled={!step1Done || !step2Done}
              >
                <IconRocket className="size-4" />
                {t("forms.wizard.createSource")}
              </GradientButton>
            )}
          </div>
        </div>
      </form>
    </Form>
  );
}
