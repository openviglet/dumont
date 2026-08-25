import { ROUTES } from "@/app/routes.const";
import { IconDatabase, type Icon as TablerIcon } from "@tabler/icons-react";
import { BentoEntityTile, BentoHero, BentoTileGrid } from "@viglet/viglet-design-system/bento";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const STANDALONE_ID = "local";

/** A surface that has moved to the bento tree, as the home mosaic lists it. */
interface MigratedSurface {
  id: string;
  titleKey: string;
  descriptionKey: string;
  icon: TablerIcon;
  /** Path under the bento integration-instance base. */
  path: string;
}

/**
 * What has migrated so far. One entry today, and it grows by one line per surface that moves.
 *
 * This is *not* the nav declaration D39 builds. That one is the whole product's map — every
 * surface, migrated or not, filtered by what a reader may see — and it feeds the rail and the
 * `⌘K` palette. This is the far smaller question of what this tree currently serves, which is the
 * only thing a home mosaic can honestly link to while the rail has no sections.
 */
const MIGRATED: MigratedSurface[] = [
  {
    id: "db-source",
    titleKey: "integration.nav.dbSources",
    descriptionKey: "integration.dbSources.description",
    icon: IconDatabase,
    path: "db-source",
  },
];

/**
 * The bento tree's index — where the rail's Home button lands (D37).
 *
 * During the migration this is the tree's only navigation, so it lists exactly the surfaces that
 * have moved. A surface that has not is reached through the console tree, which keeps serving
 * every route it has.
 */
export default function BentoHomePage() {
  const { t } = useTranslation();
  const { id } = useParams<{ id?: string }>();
  const integrationId = id ?? STANDALONE_ID;
  const base = `${ROUTES.BENTO_INTEGRATION_INSTANCE}/${integrationId}`;

  return (
    <>
      <BentoHero
        title={t("integration.nav.sources", { defaultValue: "Sources" })}
        subtitle={t("bento.home.subtitle", {
          defaultValue: "Surfaces already running on the new chrome.",
        })}
      />
      <BentoTileGrid<MigratedSurface>
        items={MIGRATED}
        tryAgainUrl={base}
        hideNew
        itemKey={(surface) => surface.id}
        renderTile={(surface) => (
          <BentoEntityTile
            to={`${base}/${surface.path}`}
            defaultIcon={surface.icon}
            title={t(surface.titleKey)}
            description={t(surface.descriptionKey)}
          />
        )}
        emptyTitle={t("bento.home.emptyTitle", { defaultValue: "Nothing has moved yet" })}
        emptyDescription={t("bento.home.emptyDescription", {
          defaultValue: "Every surface is still served by the console.",
        })}
      />
    </>
  );
}
