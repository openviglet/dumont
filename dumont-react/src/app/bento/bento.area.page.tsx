import {
  DUMONT_NAV_SECTIONS,
  dumontNavGroups,
  visibleNavSurfaces,
  type DumontNavSectionId,
} from "@/app/nav.const";
import { ROUTES } from "@/app/routes.const";
import { usePlugin } from "@/contexts/plugin.context";
import { bentoNavTarget, BentoEntityTile, BentoHero, BentoTileGrid, type BentoNavItem } from "@viglet/viglet-design-system/bento";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Navigate, useParams } from "react-router-dom";

const STANDALONE_ID = "local";

/**
 * A section hub — every surface of one band, as a mosaic (D45).
 *
 * One parameterised route rather than a page per section. The declaration already knows which
 * surfaces belong to which band and which of them a reader may see, so a hub is that list
 * rendered; a new section is a line in `DUMONT_NAV_SECTIONS` and nothing else.
 *
 * This is what puts a section on the rail. The rail lists hubs and never leaves — a fixed, small
 * height however many of the 39 surfaces migrate — so without a hub to open, a section simply
 * does not appear there.
 */
export default function BentoAreaPage() {
  const { t } = useTranslation();
  const { id, sectionId } = useParams<{ id?: string; sectionId?: string }>();
  const { providers } = usePlugin();
  const integrationId = id ?? STANDALONE_ID;

  const known = sectionId !== undefined && sectionId in DUMONT_NAV_SECTIONS;
  const section = known ? DUMONT_NAV_SECTIONS[sectionId as DumontNavSectionId] : undefined;

  const items = useMemo(() => {
    if (!known) return [];
    const visible = visibleNavSurfaces(providers);
    const group = dumontNavGroups(integrationId, visible).find((g) => g.section.id === sectionId);
    return group?.items ?? [];
  }, [known, providers, integrationId, sectionId]);

  /*
   * An unknown section is a typed or stale URL, not an empty hub. Rendering "nothing here" would
   * tell a reader the section exists and is empty, which is a different and wrong claim.
   */
  if (!known || !section) {
    return <Navigate to={`${ROUTES.BENTO_INTEGRATION_INSTANCE}/${integrationId}`} replace />;
  }

  const SectionIcon = section.icon;

  return (
    <>
      <BentoHero
        backTo={`${ROUTES.BENTO_INTEGRATION_INSTANCE}/${integrationId}`}
        backLabel={t("home.title")}
        leading={<SectionIcon size={28} aria-hidden />}
        title={t(section.labelKey)}
        subtitle={t(section.descriptionKey)}
      />
      <BentoTileGrid<BentoNavItem>
        items={items}
        tryAgainUrl={`${ROUTES.BENTO_INTEGRATION_INSTANCE}/${integrationId}${section.path}`}
        hideNew
        itemKey={(item) => item.id}
        renderTile={(item) => (
          /*
           * `bentoNavTarget` picks the surface's own bento route when it has one and its console
           * route when it does not, so a hub reaches every surface of the section throughout the
           * migration rather than only the migrated ones.
           */
          <BentoEntityTile
            to={bentoNavTarget(item)}
            defaultIcon={item.icon}
            tone={item.tone}
            title={t(item.titleKey)}
            description={t(item.descriptionKey)}
          />
        )}
        emptyTitle={t("bento.area.emptyTitle", { defaultValue: "Nothing in this section" })}
        emptyDescription={t("bento.area.emptyDescription", {
          defaultValue: "No connector plugin active here provides a surface yet.",
        })}
      />
    </>
  );
}
