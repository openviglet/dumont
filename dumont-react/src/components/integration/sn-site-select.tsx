import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  TurIntegrationTuringSitesService,
  type TuringSNSite,
} from "@/services/integration/integration-turing-sites.service";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

interface SNSiteSelectProps {
  value: string;
  onValueChange: (value: string) => void;
  integrationId: string;
  className?: string;
}

export function SNSiteSelect({
  value,
  onValueChange,
  integrationId,
  className,
}: Readonly<SNSiteSelectProps>) {
  const { t } = useTranslation();
  const [sites, setSites] = useState<TuringSNSite[]>([]);
  const [loading, setLoading] = useState(true);

  const service = useMemo(
    () => new TurIntegrationTuringSitesService(integrationId),
    [integrationId],
  );

  useEffect(() => {
    setLoading(true);
    service
      .query()
      .then(setSites)
      .catch(() => setSites([]))
      .finally(() => setLoading(false));
  }, [service]);

  return (
    <Select value={value} onValueChange={onValueChange}>
      <SelectTrigger className={className}>
        <SelectValue
          placeholder={
            loading
              ? t("forms.wizard.loadingSites")
              : t("forms.wizard.selectSite")
          }
        />
      </SelectTrigger>
      <SelectContent>
        {sites.map((site) => (
          <SelectItem key={site.id} value={site.name}>
            {site.name}
          </SelectItem>
        ))}
        {!loading && sites.length === 0 && (
          <div className="px-2 py-1.5 text-sm text-muted-foreground">
            {t("forms.wizard.noSitesFound")}
          </div>
        )}
      </SelectContent>
    </Select>
  );
}
