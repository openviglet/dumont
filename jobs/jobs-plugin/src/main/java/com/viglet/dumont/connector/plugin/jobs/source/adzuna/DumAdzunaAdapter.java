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

package com.viglet.dumont.connector.plugin.jobs.source.adzuna;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobCallBudget.Window;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobFetchContext;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobSourceAdapter;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetchException;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetcher;

import lombok.extern.slf4j.Slf4j;

/**
 * The Adzuna Brazil job source (trabalharei <strong>TRA15</strong>), for sources of type
 * {@code adzuna}.
 *
 * <p>
 * Each run reads {@code GET /v1/api/jobs/br/search/{page}} newest first ({@code sort_by=date},
 * 50 per page, {@code max_days_old} from the settings) and stops at the first page that holds a
 * posting an earlier run already fetched, at an empty page, after {@code maxPages}, or when the call
 * budget refuses. If budget remains, it then runs the same paging for one slot of the category
 * rotation ({@link DumAdzunaSettings#categories()}) and moves the source's cursor to the next slot,
 * so successive runs walk through the categories. Credentials are the source's {@code appId} and
 * {@code appKey}; {@code apiUrl}, when set, replaces the search base (up to {@code /search}).
 *
 * <p>
 * A failed call throws when nothing was fetched yet in the run (bad key, Adzuna down — the run
 * reports it); after that it ends the run with what it has, so the calls already spent are not lost.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class DumAdzunaAdapter implements DumJobSourceAdapter {

    public static final String SOURCE_TYPE = "adzuna";
    public static final String DEFAULT_API_URL = "https://api.adzuna.com/v1/api/jobs/br/search";
    public static final int RESULTS_PER_PAGE = 50;

    /** Adzuna's default plan caps: 25 a minute, 250 a day, 1,000 a week, 2,500 a month. */
    public static final Map<Window, Integer> DEFAULT_CAPS;

    static {
        Map<Window, Integer> caps = new EnumMap<>(Window.class);
        caps.put(Window.MINUTE, 25);
        caps.put(Window.DAY, 250);
        caps.put(Window.WEEK, 1_000);
        caps.put(Window.MONTH, 2_500);
        DEFAULT_CAPS = Map.copyOf(caps);
    }

    private final DumJobFetcher fetcher;
    private final Clock clock;

    @Autowired
    public DumAdzunaAdapter(DumJobFetcher fetcher) {
        this(fetcher, Clock.systemUTC());
    }

    public DumAdzunaAdapter(DumJobFetcher fetcher, Clock clock) {
        this.fetcher = fetcher;
        this.clock = clock;
    }

    @Override
    public String id() {
        return SOURCE_TYPE;
    }

    @Override
    public Map<Window, Integer> defaultCaps() {
        return DEFAULT_CAPS;
    }

    @Override
    public List<Map<String, Object>> fetch(DumJobFetchContext context) {
        DumJobSource source = context.source();
        if (isBlank(source.getAppId()) || isBlank(source.getAppKey())) {
            throw new DumJobFetchException(
                    "Adzuna source '" + source.getName() + "' needs an appId and an appKey");
        }
        DumAdzunaSettings settings = DumAdzunaSettings.parse(source.getSettings());
        Run run = new Run(context, settings, clock.instant());

        boolean refused = run.query(null);
        List<String> categories = settings.categories();
        if (!refused && !categories.isEmpty()) {
            int slot = slot(source.getCursor(), categories.size());
            int callsBefore = run.calls;
            run.query(categories.get(slot));
            if (run.calls > callsBefore) {
                source.setCursor(String.valueOf((slot + 1) % categories.size()));
            }
        }
        log.info("Adzuna source '{}': {} posting(s) from {} call(s)", source.getName(),
                run.postings.size(), run.calls);
        return new ArrayList<>(run.postings.values());
    }

    /** The rotation slot a cursor points at; an unset or unreadable cursor starts at 0. */
    static int slot(String cursor, int size) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.floorMod(Integer.parseInt(cursor.trim()), size);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** One fetch: the postings gathered so far (by id, first seen wins order) and the calls made. */
    private final class Run {
        private final DumJobFetchContext context;
        private final DumAdzunaSettings settings;
        private final Instant now;
        private final Map<String, Map<String, Object>> postings = new LinkedHashMap<>();
        private int calls;

        private Run(DumJobFetchContext context, DumAdzunaSettings settings, Instant now) {
            this.context = context;
            this.settings = settings;
            this.now = now;
        }

        /**
         * Page newest first through one query ({@code category} {@code null} = unfiltered). Answers
         * whether it stopped because the budget refused a call.
         */
        private boolean query(String category) {
            String label = category == null ? "newest" : "category " + category;
            for (int page = 1; page <= settings.maxPages(); page++) {
                if (!context.budget().tryAcquire()) {
                    log.info("Adzuna source '{}': call budget exhausted at {} page {}",
                            context.source().getName(), label, page);
                    return true;
                }
                calls++;
                JSONArray results;
                try {
                    results = results(fetcher.get(uri(category, page), Map.of()));
                } catch (DumJobFetchException e) {
                    if (postings.isEmpty()) {
                        throw e;
                    }
                    log.warn("Adzuna source '{}': {} page {} failed ({}) — keeping the {} posting(s) "
                            + "already fetched", context.source().getName(), label, page,
                            e.getMessage(), postings.size());
                    return false;
                }
                if (results.isEmpty()) {
                    return false;
                }
                if (addPage(results)) {
                    log.debug("Adzuna source '{}': {} page {} reached already-seen postings",
                            context.source().getName(), label, page);
                    return false;
                }
            }
            return false;
        }

        /** Map and keep every result of a page; answers whether any was seen by an earlier run. */
        private boolean addPage(JSONArray results) {
            boolean reachedSeen = false;
            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.optJSONObject(i);
                Map<String, Object> job = result == null ? Map.of()
                        : DumAdzunaMapper.map(result, now, settings.siteUrl());
                Object id = job.get(DumJobsSchema.ID);
                if (id != null) {
                    reachedSeen |= context.wasSeen(id.toString());
                    postings.putIfAbsent(id.toString(), job);
                }
            }
            return reachedSeen;
        }

        private URI uri(String category, int page) {
            DumJobSource source = context.source();
            String base = isBlank(source.getApiUrl()) ? DEFAULT_API_URL : source.getApiUrl().trim();
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            StringJoiner query = new StringJoiner("&");
            param(query, "app_id", source.getAppId());
            param(query, "app_key", source.getAppKey());
            param(query, "results_per_page", String.valueOf(RESULTS_PER_PAGE));
            param(query, "sort_by", "date");
            param(query, "max_days_old", String.valueOf(settings.maxDaysOld()));
            if (category != null) {
                param(query, "category", category);
                if (settings.what() != null) {
                    param(query, "what", settings.what());
                }
            }
            return URI.create(base + "/" + page + "?" + query);
        }

        private JSONArray results(String body) {
            try {
                JSONArray results = new JSONObject(body).optJSONArray("results");
                return results == null ? new JSONArray() : results;
            } catch (JSONException e) {
                throw new DumJobFetchException(
                        "Adzuna answered something that is not a JSON object: " + e.getMessage(), e);
            }
        }
    }

    private static void param(StringJoiner query, String name, String value) {
        query.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
