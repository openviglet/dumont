import { LoginForm } from "@/components/login/login-form";
import { DumontLogo } from "@/components/logo/dumont-logo";
import { ModeToggle } from "@/components/mode-toggle";
import { Globe, Search, Sparkles } from "lucide-react";
import { useTranslation } from "react-i18next";

const LoginPage = () => {
  const { t } = useTranslation();
  return (
    <div className="relative min-h-svh flex items-center justify-center overflow-hidden bg-slate-50 dark:bg-slate-950">
      <div className="absolute top-4 right-4 z-30 flex items-center gap-2">
        <ModeToggle />
      </div>

      <div className="relative z-20 flex flex-col items-center w-full max-w-md px-6 py-4">
        <div className="mb-3">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm shadow-lg ring-1 ring-blue-200/50 dark:ring-blue-500/20">
            <DumontLogo size={52} />
          </div>
        </div>

        <div className="text-center mb-1">
          <h1 className="text-2xl font-bold tracking-tight bg-linear-to-r from-blue-600 to-indigo-600 dark:from-blue-400 dark:to-indigo-400 bg-clip-text text-transparent">
            Viglet Dumont DEP
          </h1>
        </div>

        <div className="text-center mb-4">
          <p className="text-sm text-slate-500 dark:text-slate-400 max-w-xs leading-relaxed">
            {t("login.heroTagline")}
          </p>
        </div>

        <div className="flex flex-wrap justify-center gap-2 mb-5">
          <div className="flex items-center gap-1.5 rounded-full px-3 py-1 bg-white/70 dark:bg-slate-800/60 backdrop-blur-sm border border-slate-200/60 dark:border-slate-700/60">
            <Search className="h-3.5 w-3.5 text-blue-500" />
            <span className="text-xs font-medium text-slate-600 dark:text-slate-300">
              {t("login.featureIndexing")}
            </span>
          </div>
          <div className="flex items-center gap-1.5 rounded-full px-3 py-1 bg-white/70 dark:bg-slate-800/60 backdrop-blur-sm border border-slate-200/60 dark:border-slate-700/60">
            <Sparkles className="h-3.5 w-3.5 text-indigo-500" />
            <span className="text-xs font-medium text-slate-600 dark:text-slate-300">
              {t("login.featureConnector")}
            </span>
          </div>
          <div className="flex items-center gap-1.5 rounded-full px-3 py-1 bg-white/70 dark:bg-slate-800/60 backdrop-blur-sm border border-slate-200/60 dark:border-slate-700/60">
            <Globe className="h-3.5 w-3.5 text-cyan-500" />
            <span className="text-xs font-medium text-slate-600 dark:text-slate-300">
              {t("login.featureExchange")}
            </span>
          </div>
        </div>

        <div className="w-full">
          <div className="rounded-2xl bg-white/75 dark:bg-slate-900/70 backdrop-blur-md border border-white/40 dark:border-slate-700/50 shadow-xl p-2">
            <LoginForm />
          </div>
        </div>

        <div className="mt-4 text-center">
          <p className="text-xs text-slate-400 dark:text-slate-500">
            Viglet Dumont DEP &mdash; Data Exchange Platform
          </p>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
