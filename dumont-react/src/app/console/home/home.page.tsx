import { ROUTES } from "@/app/routes.const"
import { DumontLogo } from "@/components/logo/dumont-logo"
import { PageHeader } from "@/components/page-header"
import type { Icon } from "@tabler/icons-react"
import {
  IconAdjustmentsSearch,
  IconBook,
  IconChartBar,
  IconExternalLink,
  IconGitCommit,
  IconGraph,
  IconHome,
  IconInfoCircle,
  IconListCheck,
  IconTools,
} from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { NavLink } from "react-router-dom"

interface FeatureCard {
  titleKey: string
  descriptionKey: string
  url: string
  icon: Icon
  docUrl?: string
}

interface FeatureSection {
  labelKey: string
  descriptionKey: string
  gradient: string
  items: FeatureCard[]
}

/** Fixed id used for standalone routes — the axios interceptor strips it from API calls. */
const STANDALONE_ID = "local"
const BASE = `${ROUTES.INTEGRATION_INSTANCE}/${STANDALONE_ID}`

const SECTIONS: FeatureSection[] = [
  {
    labelKey: "home.sections.aem.label",
    descriptionKey: "home.sections.aem.description",
    gradient: "from-teal-600 to-emerald-600",
    items: [
      { titleKey: "home.features.sources.title", descriptionKey: "home.features.sources.description", url: `${BASE}/source`, icon: IconGitCommit },
      { titleKey: "home.features.indexingRules.title", descriptionKey: "home.features.indexingRules.description", url: `${BASE}/indexing-rule`, icon: IconTools },
      { titleKey: "home.features.indexingManager.title", descriptionKey: "home.features.indexingManager.description", url: `${BASE}/indexing-manager`, icon: IconAdjustmentsSearch },
      { titleKey: "home.features.monitoring.title", descriptionKey: "home.features.monitoring.description", url: `${BASE}/monitoring`, icon: IconGraph },
      { titleKey: "home.features.indexingStats.title", descriptionKey: "home.features.indexingStats.description", url: `${BASE}/indexing-stats`, icon: IconChartBar },
      { titleKey: "home.features.doubleCheck.title", descriptionKey: "home.features.doubleCheck.description", url: `${BASE}/double-check`, icon: IconListCheck },
      { titleKey: "home.features.systemInfo.title", descriptionKey: "home.features.systemInfo.description", url: `${BASE}/system-info`, icon: IconInfoCircle },
    ],
  },
]

function getGreetingKey(): string {
  const hour = new Date().getHours()
  if (hour < 12) return "home.greeting.morning"
  if (hour < 18) return "home.greeting.afternoon"
  return "home.greeting.evening"
}

export default function HomePage() {
  const { t } = useTranslation()

  return (
    <>
      <PageHeader turIcon={IconHome} title={t("home.title")} />
      <div className="px-6 py-8 max-w-7xl mx-auto">
        {/* Hero */}
        <div className="mb-10">
          <div className="flex items-center gap-3 mb-2">
            <DumontLogo className="size-10" />
            <div>
              <h1 className="text-3xl font-bold tracking-tight">
                {t(getGreetingKey())}
              </h1>
              <p className="text-muted-foreground text-sm mt-1">
                {t("home.welcome")}
              </p>
            </div>
          </div>
        </div>

        {/* Sections */}
        <div className="space-y-10">
          {SECTIONS.map((section) => (
            <section key={section.labelKey}>
              <div className="mb-4">
                <h2 className="text-xl font-semibold">{t(section.labelKey)}</h2>
                <p className="text-sm text-muted-foreground mt-0.5">{t(section.descriptionKey)}</p>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                {section.items.map((item) => {
                  const ItemIcon = item.icon
                  return (
                    <div key={item.titleKey} className="relative flex flex-col h-full rounded-xl border bg-card p-5 transition-all duration-200">
                      <div className={`inline-flex rounded-lg bg-gradient-to-br ${section.gradient} p-2.5 mb-3 self-start`}>
                        <ItemIcon className="size-5 text-white" />
                      </div>
                      <h3 className="font-medium text-sm mb-1">
                        {t(item.titleKey)}
                      </h3>
                      <p className="text-xs text-muted-foreground leading-relaxed mb-4 flex-1">
                        {t(item.descriptionKey)}
                      </p>
                      <div className="flex items-center justify-between pt-2 border-t border-border/50">
                        {item.docUrl ? (
                          <a
                            href={item.docUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
                          >
                            <IconBook className="size-3.5" />
                            {t("home.docs")}
                          </a>
                        ) : <span />}
                        <NavLink
                          to={item.url}
                          className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
                        >
                          {t("home.open")}
                          <IconExternalLink className="size-3.5" />
                        </NavLink>
                      </div>
                    </div>
                  )
                })}
              </div>
            </section>
          ))}
        </div>
      </div>
    </>
  )
}
