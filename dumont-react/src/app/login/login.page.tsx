import { LoginForm } from "@/components/login/login-form";
import { DumontLogo } from "@/components/logo/dumont-logo";
import { ModeToggle } from "@/components/mode-toggle";
import { Login } from "@viglet/viglet-design-system";
import { Globe, Search, Sparkles } from "lucide-react";
import { useTranslation } from "react-i18next";
import { LoginSettingsButton, useLoginSettings } from "./login-settings";

const LoginPage = () => {
  const { t } = useTranslation();
  const { settings, update } = useLoginSettings();

  return (
    <Login color="#059669" colorDark="#10b981">
      <Login.Background
        withFormulas={settings.formulas}
        withBonds={settings.formulas}
        withLightning={settings.animations}
        withExplosion={settings.explosion}
        withOrbs={settings.animations}
        withGrid
      />

      <Login.Settings>
        <LoginSettingsButton settings={settings} onUpdate={update} />
        <ModeToggle />
      </Login.Settings>

      <Login.Content>
        <Login.Logo pulsePaused={!settings.animations}>
          <DumontLogo size={52} />
        </Login.Logo>

        <Login.Title>Viglet Dumont DEP</Login.Title>
        <Login.Tagline>{t("login.heroTagline")}</Login.Tagline>

        <Login.Features>
          <Login.FeaturePill icon={<Search className="h-3.5 w-3.5 text-emerald-500" />}>
            {t("login.featureIndexing")}
          </Login.FeaturePill>
          <Login.FeaturePill icon={<Sparkles className="h-3.5 w-3.5 text-teal-500" />}>
            {t("login.featureConnector")}
          </Login.FeaturePill>
          <Login.FeaturePill icon={<Globe className="h-3.5 w-3.5 text-cyan-500" />}>
            {t("login.featureExchange")}
          </Login.FeaturePill>
        </Login.Features>

        <Login.Card>
          <LoginForm />
        </Login.Card>

        <Login.Footer>Viglet Dumont DEP &mdash; Data Exchange Platform</Login.Footer>
      </Login.Content>
    </Login>
  );
};

export default LoginPage;
