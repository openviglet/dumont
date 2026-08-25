import { DUMONT_NAV, resolveIcon } from "@/app/nav.const";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@viglet/viglet-design-system/bento";
import { useEffect, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Load a source list, in the one shape every source service already offers.
 *
 * `items` starts `undefined` and only becomes an array once the request answers — that gap is the
 * package's loading contract, and it is not the same value as `[]`. A page defaulting to an empty
 * array shows every reader the blank slate for the length of the request, which looks like a
 * connector with no sources rather than one that is still answering.
 */
export function useSourceList<T>(service: Readonly<{ query: () => Promise<T[]> }>) {
  const [items, setItems] = useState<T[]>();
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();

  useEffect(() => {
    service
      .query()
      .then(setItems)
      .catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
  }, [service, t]);

  return { items, error, setItems };
}

/**
 * The shape every source list has, in bento chrome (D46).
 *
 * Six source lists render the same thing — a header, a grid of cards, a blank slate — differing
 * only in which service answers, which two fields a card shows, and their translation keys. The
 * console era wrote that shape out six times; copying it into the bento era six more would be the
 * duplication the design system exists to prevent, one level down.
 *
 * What is *not* passed in is anything the navigation declaration already knows: the surface's
 * path, its icon and its tone are looked up by id. That is the second payoff of D39 — a surface
 * renamed or re-toned in one place changes here too, and a page cannot disagree with the rail
 * about what it is called.
 */
export interface BentoSourceListProps<T> {
  /** The surface's id in `DUMONT_NAV` — supplies its path, icon and tone. */
  surfaceId: string;
  /**
   * Translation key prefix, e.g. `integration.dbSources`. Reuses the console's own keys: the same
   * entity in two chromes is the same entity, and a parallel namespace would let the two eras
   * disagree about what a source is called.
   */
  i18nPrefix: string;
  /** Query result — `undefined` while loading, which is not the same as an empty list. */
  items: T[] | undefined;
  error: string | null;
  itemKey: (item: T) => string;
  itemTitle: (item: T) => string;
  /** The second line on a card — an endpoint, a directory, a URL. */
  itemDescription: (item: T) => string | null | undefined;
  /** Extra action rendered beside the hero, e.g. AEM's import-from-JSON. */
  headerAction?: ReactNode;
}

export function BentoSourceList<T>({
  surfaceId,
  i18nPrefix,
  items,
  error,
  itemKey,
  itemTitle,
  itemDescription,
  headerAction,
}: Readonly<BentoSourceListProps<T>>) {
  const { t } = useTranslation();
  const { id } = useParams() as { id: string };

  const surface = DUMONT_NAV.find((s) => s.id === surfaceId);
  if (!surface) throw new Error(`bento source list: no surface "${surfaceId}" in DUMONT_NAV`);

  const bentoBase = `${ROUTES.BENTO_INTEGRATION_INSTANCE}/${id}`;
  /*
   * Detail and "new" pages have not migrated, so both link into the console tree. Named through
   * CONSOLE_INTEGRATION_INSTANCE rather than INTEGRATION_INSTANCE: the latter reads its prefix
   * from the live URL, which from inside this shell is `/bento`, and every card would point at a
   * route nothing serves.
   */
  const consoleBase = `${ROUTES.CONSOLE_INTEGRATION_INSTANCE}/${id}${surface.path}`;

  return (
    <BentoListPage<T>
      items={items}
      error={error}
      tryAgainUrl={`${bentoBase}${surface.path}`}
      backTo={bentoBase}
      backLabel={t("home.title")}
      heroIcon={resolveIcon(surface.iconName)}
      tone={surface.tone}
      title={t(`${i18nPrefix}.title`)}
      subtitle={t(`${i18nPrefix}.description`)}
      headerAction={headerAction}
      newRoute={`${consoleBase}/new`}
      newLabel={t(`${i18nPrefix}.newSource`)}
      newSubtitle={t(`${i18nPrefix}.feature`)}
      itemKey={itemKey}
      renderTile={(item, emphasis) => (
        <BentoEntityTile
          to={`${consoleBase}/${itemKey(item)}`}
          emphasis={emphasis}
          defaultIcon={resolveIcon(surface.iconName)}
          tone={surface.tone}
          title={itemTitle(item)}
          description={itemDescription(item)}
        />
      )}
      emptyTitle={t(`${i18nPrefix}.blankTitle`)}
      emptyDescription={t(`${i18nPrefix}.blankDescription`)}
    />
  );
}
