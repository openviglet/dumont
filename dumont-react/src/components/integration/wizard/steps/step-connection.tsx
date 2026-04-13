import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import {
  IconCheck,
  IconPlugConnected,
  IconX,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useState } from "react";
import type { UseFormReturn } from "react-hook-form";
import { useTranslation } from "react-i18next";

interface StepConnectionProps {
  form: UseFormReturn<TurIntegrationAemSource>;
  integrationId: string;
  onConnectionTested: (success: boolean) => void;
  connectionTested: boolean;
}

export function StepConnection({
  form,
  integrationId,
  onConnectionTested,
  connectionTested,
}: Readonly<StepConnectionProps>) {
  const { t } = useTranslation();
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<boolean | null>(null);

  async function handleTestConnection() {
    const valid = await form.trigger(["endpoint", "username", "password"]);
    if (!valid) return;

    setTesting(true);
    setTestResult(null);
    try {
      const service = new TurIntegrationAemSourceService(integrationId);
      const result = await service.testConnection({
        endpoint: form.getValues("endpoint"),
        username: form.getValues("username"),
        password: form.getValues("password"),
      });
      setTestResult(result.success);
      onConnectionTested(result.success);
      if (result.success) {
        toast.success(t("forms.wizard.testConnectionSuccess"));
      } else {
        toast.error(result.message || t("forms.wizard.testConnectionFailed"));
      }
    } catch {
      setTestResult(false);
      onConnectionTested(false);
      toast.error(t("forms.wizard.testConnectionFailed"));
    } finally {
      setTesting(false);
    }
  }

  return (
    <div className="space-y-4 max-w-lg">
      <div>
        <h3 className="text-base font-semibold">
          {t("forms.wizard.stepConnection")}
        </h3>
        <p className="text-sm text-muted-foreground mt-1">
          {t("forms.wizard.stepConnectionDesc")}
        </p>
      </div>

      <FormField
        control={form.control}
        name="endpoint"
        rules={{ required: t("forms.integration.endpointRequired") }}
        render={({ field }) => (
          <FormItem>
            <FormLabel>{t("forms.integrationSource.endpoint")}</FormLabel>
            <FormControl>
              <Input
                {...field}
                placeholder="http://localhost:4502"
                type="url"
                onChange={(e) => {
                  field.onChange(e);
                  setTestResult(null);
                  onConnectionTested(false);
                }}
              />
            </FormControl>
            <FormDescription>
              {t("forms.wizard.endpointDesc")}
            </FormDescription>
            <FormMessage />
          </FormItem>
        )}
      />

      <div className="grid grid-cols-2 gap-4">
        <FormField
          control={form.control}
          name="username"
          rules={{
            required: t("forms.integrationSource.username") + " is required.",
          }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t("forms.integrationSource.username")}</FormLabel>
              <FormControl>
                <Input
                  {...field}
                  placeholder="admin"
                  type="text"
                  onChange={(e) => {
                    field.onChange(e);
                    setTestResult(null);
                    onConnectionTested(false);
                  }}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="password"
          rules={{
            required: t("forms.integrationSource.password") + " is required.",
          }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t("forms.integrationSource.password")}</FormLabel>
              <FormControl>
                <Input
                  {...field}
                  placeholder="••••••••"
                  type="password"
                  onChange={(e) => {
                    field.onChange(e);
                    setTestResult(null);
                    onConnectionTested(false);
                  }}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
      </div>

      <div className="flex items-center gap-3 pt-2">
        <GradientButton
          type="button"
          variant={testResult === true ? "success" : "outline"}
          onClick={handleTestConnection}
          loading={testing}
        >
          <IconPlugConnected className="size-4" />
          {t("forms.wizard.testConnection")}
        </GradientButton>

        {testResult === true && !testing && (
          <span className="flex items-center gap-1.5 text-sm text-emerald-600 font-medium">
            <IconCheck className="size-4" />
            {t("forms.wizard.connectionOk")}
          </span>
        )}
        {testResult === false && !testing && (
          <span className="flex items-center gap-1.5 text-sm text-destructive font-medium">
            <IconX className="size-4" />
            {t("forms.wizard.connectionFailed")}
          </span>
        )}
        {!connectionTested && testResult === null && !testing && (
          <span className="text-sm text-muted-foreground">
            {t("forms.wizard.testConnectionHint")}
          </span>
        )}
      </div>
    </div>
  );
}
