import {
  DUMONT_NAV_SECTIONS,
  dumontNavGroups,
  visibleNavSurfaces,
  type DumontNavSectionId,
} from "@/app/nav.const";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoHero, BentoTileGrid } from "@viglet/viglet-design-system/bento";
import type { BentoNavGroup } from "@viglet/viglet-design-system/bento";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const STANDALONE_ID = "local";

/**
 * The bento tree's index — where the rail's Home button lands.
 *
 * It lists the **sections**, matching the rail exactly, and each tile opens that section's hub
 * (D45). Home → hub → surface is the hierarchy the rail assumes, and a home that listed leaves
 * instead would be a third, flatter answer to a question the declaration already answers twice.
 *
 * It used to carry a hand-written array of migrated surfaces. That was a second list of something
 * `DUMONT_NAV` already records — `migrated` — and the kind of copy that is right until the day a
 * surface moves and nobody edits both.
 */
export default function BentoHomePage() {
  const { t } = useTranslation();
  const { id } = useParams<{ id?: string }>();
  const integrationId = id ?? STANDALONE_ID;
  const base = `${ROUTES.BENTO_INTEGRATION_INSTANCE}/${integrationId}`;

  /*
   * Unfiltered by connector plugin, on purpose: a section is a place, and a console that has no
   * database plugin still has a Sources section — the hub behind it is what shows an empty state.
   * The surfaces inside every hub *are* filtered, which is where visibility actually matters.
   */
  const sections = useMemo(
    () => dumontNavGroups(integrationId, visibleNavSurfaces([])),
    [integrationId],
  );

  return (
    <>
      <BentoHero
        title={t("sidebar.brandName")}
        subtitle={t("bento.home.subtitle", { defaultValue: "Choose a section to begin." })}
      />
      <BentoTileGrid<BentoNavGroup>
        items={sections}
        tryAgainUrl={base}
        hideNew
        itemKey={(group) => group.section.id}
        renderTile={(group) => {
          const declared = DUMONT_NAV_SECTIONS[group.section.id as DumontNavSectionId];
          return (
            <BentoEntityTile
              to={group.section.areaRoute!}
              defaultIcon={declared.icon}
              tone={declared.tone}
              title={t(declared.labelKey)}
              description={t(declared.descriptionKey)}
            />
          );
        }}
        emptyTitle={t("bento.home.emptyTitle", { defaultValue: "Nothing has moved yet" })}
        emptyDescription={t("bento.home.emptyDescription", {
          defaultValue: "Every surface is still served by the console.",
        })}
      />
    </>
  );
}
