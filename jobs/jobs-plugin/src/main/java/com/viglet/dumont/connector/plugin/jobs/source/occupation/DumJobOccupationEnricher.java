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

package com.viglet.dumont.connector.plugin.jobs.source.occupation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassifier;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnricher;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeLibrary;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeRef;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumTaxonomy;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobCall;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobOccupationCacheEntry;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobCallRepository;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobOccupationCacheRepository;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobCallBudget;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;

import lombok.extern.slf4j.Slf4j;

/**
 * The single canonical occupation step of every job record (trabalharei <strong>TRA22</strong>): a D04
 * {@link DumEnricher} that writes {@code occupation} as the slug of a CBO 2002 family
 * ({@link DumCboFamilies}), read from the title alone. No partner sends a CBO family; {@code extract}
 * leaves the field off the record (D98) so this step stays the only writer.
 *
 * <ol>
 * <li><strong>Dictionary.</strong> {@link DumJobOccupationDictionary} reads the frequent titles for
 * free.</li>
 * <li><strong>Cache.</strong> A title the dictionary does not read is looked up by the SHA-256 of its
 * {@link DumJobOccupationDictionary#normalize normalized} form in {@code job_occupation_cache}
 * ({@link DumJobOccupationCacheEntry}); a hit answers without a call, including a cached "no
 * family".</li>
 * <li><strong>Classifier.</strong> On a miss, the commons D04 {@link DumClassifier} is asked, with the
 * family list as its closed label set ({@link DumJobOccupationLabels}), under a rolling daily cap of
 * {@code dailyCap} calls. Every call the cap grants is written to {@code job_call} under
 * {@link #LLM_CALLS_ID}, so the cap holds across runs and restarts the way partner caps do. An answer
 * outside the list is "no family". The answer is cached; a failed or refused call is not.</li>
 * </ol>
 *
 * <p>
 * Nothing is ever guessed: no classifier, a cap reached, a failed call or a "no family" answer all
 * leave {@code occupation} absent. With no classifier (today's production wiring, see
 * {@code DumJobsPlugin}) the dictionary alone runs.
 *
 * <p>
 * The family list is the D19 taxonomy {@link DumCboFamilies#TAXONOMY}, resolved from a
 * {@link DumRecipeLibrary} on every record, so a newer version applies on the next record; a cached
 * answer from another version is a miss.
 *
 * <p>
 * Why not {@code DumClassificationEnricher}: its cache is in memory only, it caches every empty result
 * (so a call refused by the cap would read as "no family" for the life of the process), it has no call
 * cap, and it publishes whatever label the classifier returns instead of checking it against a list.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumJobOccupationEnricher implements DumEnricher {

    /** The enricher name, also the provenance tag of the field it writes. */
    public static final String NAME = "job-occupation";

    /** The bean name a {@link DumClassifier} for occupations is looked up by. */
    public static final String CLASSIFIER_BEAN = "jobsOccupationClassifier";

    /** The {@code job_call.source_id} the classifier calls are counted under. */
    public static final String LLM_CALLS_ID = "occupation-classifier";

    /** Classifier calls allowed in any rolling 24 hours when no setting says otherwise. */
    public static final int DEFAULT_DAILY_CAP = 500;

    /** Per-source counts of one run; {@code capped} titles are also counted as unclassified. */
    public record Summary(int records, int dictionary, int cached, int classified, int unclassified,
            int capped) {
    }

    private static final Summary EMPTY = new Summary(0, 0, 0, 0, 0, 0);

    private static final class Counter {
        final AtomicInteger records = new AtomicInteger();
        final AtomicInteger dictionary = new AtomicInteger();
        final AtomicInteger cached = new AtomicInteger();
        final AtomicInteger classified = new AtomicInteger();
        final AtomicInteger unclassified = new AtomicInteger();
        final AtomicInteger capped = new AtomicInteger();

        Summary snapshot() {
            return new Summary(records.get(), dictionary.get(), cached.get(), classified.get(),
                    unclassified.get(), capped.get());
        }
    }

    private final DumRecipeLibrary library;
    private final DumRecipeRef taxonomyRef;
    private final DumClassifier classifier;
    private final DumJobOccupationCacheRepository cacheRepository;
    private final DumJobCallRepository callRepository;
    private final int dailyCap;
    private final Clock clock;
    private final Map<String, DumJobOccupationCacheEntry> memoryCache = new ConcurrentHashMap<>();
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private DumJobCallBudget budget;

    /**
     * @param classifier      the fallback, or {@code null} for the dictionary alone
     * @param cacheRepository the persistent cache, or {@code null} to cache in memory
     * @param callRepository  where granted calls are recorded, or {@code null} to count in memory
     * @param dailyCap        classifier calls allowed per rolling 24 hours; {@code <= 0} allows none
     */
    public DumJobOccupationEnricher(DumRecipeLibrary library, DumRecipeRef taxonomyRef,
            DumClassifier classifier, DumJobOccupationCacheRepository cacheRepository,
            DumJobCallRepository callRepository, int dailyCap, Clock clock) {
        this.library = library;
        this.taxonomyRef = taxonomyRef;
        this.classifier = classifier;
        this.cacheRepository = cacheRepository;
        this.callRepository = callRepository;
        this.dailyCap = dailyCap;
        this.clock = clock;
    }

    /** The production enricher over the shipped CBO list, tracked at its latest version. */
    public static DumJobOccupationEnricher standard(DumClassifier classifier,
            DumJobOccupationCacheRepository cacheRepository, DumJobCallRepository callRepository,
            int dailyCap) {
        return new DumJobOccupationEnricher(library(), DumRecipeRef.latest(DumCboFamilies.TAXONOMY),
                classifier, cacheRepository, callRepository, dailyCap, Clock.systemUTC());
    }

    /** The dictionary alone, over the shipped CBO list: no classifier, nothing persisted. */
    public static DumJobOccupationEnricher dictionaryOnly() {
        return standard(null, null, null, 0);
    }

    /**
     * A library private to the jobs connector holding the shipped CBO list (as with the IBGE list, a
     * {@code DumRecipeLibrary} bean would make the catalog connector's injection ambiguous).
     */
    public static DumRecipeLibrary library() {
        return new DumRecipeLibrary().register(DumCboFamilies.load());
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
        Counter counter = counters.computeIfAbsent(source.getSourceName(), n -> new Counter());
        counter.records.incrementAndGet();
        String title = title(rec);
        DumTaxonomy taxonomy = library.taxonomy(taxonomyRef).orElse(null);
        String code = null;
        if (title != null && taxonomy != null) {
            code = DumJobOccupationDictionary.classify(title)
                    .filter(known -> taxonomy.table().containsKey(known))
                    .orElse(null);
            if (code != null) {
                counter.dictionary.incrementAndGet();
            } else {
                code = fallback(source, rec, title, taxonomy, counter);
            }
        }
        if (code == null) {
            counter.unclassified.incrementAndGet();
            return Map.of();
        }
        return Map.of(DumJobsSchema.OCCUPATION, DumCboFamilies.slugOf(taxonomy, code));
    }

    /** The cached or classified code for a title the dictionary did not read, or {@code null}. */
    private String fallback(DumStructuredSource source, DumStructuredRecord rec, String title,
            DumTaxonomy taxonomy, Counter counter) {
        String normalized = DumJobOccupationDictionary.normalize(title);
        if (normalized.isEmpty() || classifier == null) {
            return null;
        }
        String key = hash(normalized);
        Optional<DumJobOccupationCacheEntry> cached = cached(key)
                .filter(entry -> entry.getTaxonomyVersion() == taxonomy.version());
        if (cached.isPresent()) {
            counter.cached.incrementAndGet();
            String code = cached.get().getCode();
            return code != null && taxonomy.table().containsKey(code) ? code : null;
        }
        if (dailyCap <= 0 || !budget().tryAcquire()) {
            counter.capped.incrementAndGet();
            log.debug("Job '{}' in source '{}': occupation classifier cap reached, '{}' left unclassified",
                    rec.id(), source.getSourceName(), normalized);
            return null;
        }
        Optional<String> answer;
        try {
            answer = classifier.classify(DumJobNormalizer.plainText(title));
        } catch (RuntimeException e) {
            log.warn("Occupation classifier failed for '{}', left unclassified: {}", normalized,
                    e.getMessage());
            return null;
        }
        String code = answer == null ? null
                : answer.flatMap(text -> DumJobOccupationLabels.code(text, taxonomy)).orElse(null);
        store(DumJobOccupationCacheEntry.builder().titleHash(key).normalizedTitle(truncate(normalized))
                .code(code).taxonomyVersion(taxonomy.version()).classifiedAt(clock.instant()).build());
        if (code != null) {
            counter.classified.incrementAndGet();
        }
        return code;
    }

    /**
     * The occupation slug this step already knows for {@code title} &mdash; the dictionary's, else a
     * cached classifier answer &mdash; without calling the classifier and without counting
     * (trabalharei <strong>TRA24</strong>: a tombstone names the occupation the document carried).
     */
    public Optional<String> known(String title) {
        DumTaxonomy taxonomy = library.taxonomy(taxonomyRef).orElse(null);
        if (title == null || title.isBlank() || taxonomy == null) {
            return Optional.empty();
        }
        String code = DumJobOccupationDictionary.classify(title)
                .filter(known -> taxonomy.table().containsKey(known))
                .orElse(null);
        if (code == null) {
            String normalized = DumJobOccupationDictionary.normalize(title);
            code = normalized.isEmpty() ? null
                    : cached(hash(normalized))
                            .filter(entry -> entry.getTaxonomyVersion() == taxonomy.version())
                            .map(DumJobOccupationCacheEntry::getCode)
                            .filter(cachedCode -> taxonomy.table().containsKey(cachedCode))
                            .orElse(null);
        }
        return code == null ? Optional.empty() : Optional.of(DumCboFamilies.slugOf(taxonomy, code));
    }

    /** This source's counts since the last {@link #logSummary} / {@link #reset}, without clearing them. */
    public Summary summary(String sourceName) {
        Counter counter = counters.get(sourceName);
        return counter == null ? EMPTY : counter.snapshot();
    }

    /** Forget this source's counts — called when a run starts. */
    public void reset(String sourceName) {
        counters.remove(sourceName);
    }

    /** Log one summary line for the source's run and clear its counts (D100: no end-of-run hook). */
    public Summary logSummary(String sourceName) {
        Counter counter = counters.remove(sourceName);
        Summary summary = counter == null ? EMPTY : counter.snapshot();
        String line = "Job source '{}' occupations: {} record(s), {} by dictionary, {} cached, "
                + "{} classified, {} unclassified ({} refused by the daily classifier cap of {})";
        Object[] args = { sourceName, summary.records(), summary.dictionary(), summary.cached(),
                summary.classified(), summary.unclassified(), summary.capped(), dailyCap };
        if (summary.capped() > 0) {
            log.warn(line, args);
        } else {
            log.info(line, args);
        }
        return summary;
    }

    /** Whether a classifier is wired; without one only the dictionary runs. */
    public boolean hasClassifier() {
        return classifier != null;
    }

    /**
     * The shared daily budget, built on first use and seeded with the calls of the last 24 hours, so
     * every source and every run of this process draws on the same cap.
     */
    private synchronized DumJobCallBudget budget() {
        if (budget == null) {
            Instant now = clock.instant();
            List<Instant> prior = List.of();
            if (callRepository != null) {
                callRepository.deleteBySourceIdAndCalledAtLessThanEqual(LLM_CALLS_ID,
                        now.minus(Duration.ofDays(DumJobCall.RETENTION_DAYS)));
                prior = callRepository.findBySourceIdAndCalledAtAfter(LLM_CALLS_ID,
                        now.minus(DumJobCallBudget.Window.DAY.length()))
                        .stream().map(DumJobCall::getCalledAt).toList();
            }
            DumJobCallBudget.Builder builder = DumJobCallBudget.builder(clock)
                    .cap(DumJobCallBudget.Window.DAY, dailyCap).priorCalls(prior);
            if (callRepository != null) {
                builder.onGranted(at -> callRepository
                        .save(DumJobCall.builder().sourceId(LLM_CALLS_ID).calledAt(at).build()));
            }
            budget = builder.build();
        }
        return budget;
    }

    private Optional<DumJobOccupationCacheEntry> cached(String key) {
        return cacheRepository != null ? cacheRepository.findById(key)
                : Optional.ofNullable(memoryCache.get(key));
    }

    private void store(DumJobOccupationCacheEntry entry) {
        if (cacheRepository != null) {
            cacheRepository.save(entry);
        } else {
            memoryCache.put(entry.getTitleHash(), entry);
        }
    }

    private static String title(DumStructuredRecord rec) {
        Object title = rec.values().get(DumJobsSchema.TITLE);
        if (title instanceof Collection<?> values) {
            title = values.isEmpty() ? null : values.iterator().next();
        }
        return title == null || title.toString().isBlank() ? null : title.toString();
    }

    /** SHA-256 of {@code text} in UTF-8, as 64 hex characters: the cache key of a normalized title. */
    public static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String truncate(String text) {
        return text.length() <= 512 ? text : text.substring(0, 512);
    }
}
