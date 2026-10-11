/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.commons.spi.structured;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The structured-source SPI: a connector contract for sources whose value is their
 * <em>structure</em> (tabular listings, per-item detail pages with a typed schema) rather than
 * free-form HTML.
 *
 * <p>
 * A source author implements two operations — {@link #discover()} to enumerate the content ids, and
 * {@link #extract(String)} to turn one id into a schema-bound, typed {@link DumStructuredRecord} —
 * plus a declared {@link #schema()}. The generic indexing loop (discover &rarr; extract &rarr;
 * ground &rarr; publish) is provided once by {@link DumStructuredSourcePlugin}, so a new source is a
 * small declarative adapter, not a bespoke crawler. This generalizes the AEM connector's shape
 * (discover paths, extract a typed object) to arbitrary structured web sources, sitting above the
 * generic web-crawler link-spider.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface DumStructuredSource {

    /** The source name/id as received by the connector API. */
    String getSourceName();

    /** The provider name reported for routing (e.g. {@code "STRUCTURED"}, a sub-type, ...). */
    String getProviderName();

    /** The Turing SN sites this source publishes to. */
    Collection<String> getSiteNames();

    /** The field manifest this source declares once and binds every record to. */
    DumStructuredSchema schema();

    /**
     * Enumerate the ids of every item this source can index. Implementations typically parse a
     * sitemap, a listing page, or a tabular catalog. Returning an empty list is valid.
     */
    List<String> discover();

    /**
     * Extract one item into a typed, schema-bound record. Returns {@link Optional#empty()} when the
     * id cannot be extracted (not found, transient error) — the indexing loop skips it rather than
     * publishing an empty document.
     */
    Optional<DumStructuredRecord> extract(String contentId);

    /**
     * Whether the last {@link #discover()} reached every item the source holds (roadmap D92). A
     * source that stops early — a call budget spent, a page cap hit, a page that failed — answers
     * {@code false}, and the indexing loop then finishes the run standalone so the items it never
     * reached are not deindexed. Defaults to {@code true}: a source that cannot be cut short has
     * nothing to report.
     */
    default boolean discoveryComplete() {
        return true;
    }

    /**
     * Whether this source is incremental (roadmap D92): no single run is expected to see every
     * item, so a run never sweeps what it did not reach; items leave the index through
     * {@link #expiredIds()} instead. Defaults to {@code false}.
     */
    default boolean incremental() {
        return false;
    }

    /**
     * For an {@link #incremental()} source, the ids gone long enough to deindex, read once after a
     * run's extraction. Defaults to none.
     */
    default Collection<String> expiredIds() {
        return List.of();
    }

    /**
     * Ids still present at the source but not re-extracted this run (roadmap D104), read once
     * after {@link #discover()}: a quota-bound API that fetches only new items knows the older
     * ones are still open. The indexing loop keeps them alive, so a sweeping finish does not
     * deindex them and the source needs no stored payload to replay. They must not also be in
     * {@link #discover()}. Defaults to none.
     */
    default Collection<String> unchangedIds() {
        return List.of();
    }

    /**
     * Short notes on how the last run went, read once after extraction (roadmap D96): why a walk
     * stopped early ("daily allowance of 250 calls spent", "partner answered HTTP 429") or anything
     * else an operator should see in the run record rather than only in the logs. Defaults to none.
     */
    default List<String> runNotes() {
        return List.of();
    }

    /** The locale of the published documents; defaults to {@link Locale#US}. */
    default Locale getLocale() {
        return Locale.US;
    }
}
