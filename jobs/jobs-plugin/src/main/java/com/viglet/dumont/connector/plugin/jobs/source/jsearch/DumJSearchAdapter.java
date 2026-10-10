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

package com.viglet.dumont.connector.plugin.jobs.source.jsearch;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.StringJoiner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobCallBudget;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobCallBudget.Window;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobFetchContext;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobSourceAdapter;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetchException;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetcher;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobHttpFetcher;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * The JSearch job source on RapidAPI (trabalharei <strong>TRA19</strong>), for sources of type
 * {@code jsearch}.
 *
 * <p>
 * Each run takes one slot of the query rotation ({@link DumJSearchSettings#queries()}) and pages it
 * with {@code GET https://jsearch.p.rapidapi.com/search?query=&page=&num_pages=1&country=br&language=pt&date_posted=}
 * ({@code date_posted} from the settings, {@code week} by default). Paging stops at an empty page, at a
 * page whose every Brazilian posting an earlier run already fetched, after {@code maxPages} (2 by
 * default), or at the first refused or failed call. The source's cursor moves to the next query only
 * when a call was made, so a run the budget refuses outright retries the same query next time.
 * {@code /job-details} is never called: it would double the cost, and {@code /search} carries every
 * field the mapper reads.
 *
 * <p>
 * <strong>JSearch is paid per call</strong>, so the budget is a hard cap: the default caps are
 * conservative ({@link #DEFAULT_CAPS}), a refused call ends the run at once, and a failed call is
 * never retried within the run. When the 30-day cap is what refused, the run logs one line naming the
 * source and saying the monthly budget is spent.
 *
 * <p>
 * The credential is the source's {@code apiKey}, sent as the {@code X-RapidAPI-Key} header; it is
 * passed to the fetcher as a request secret, so it is masked in every log line and error message. The
 * search endpoint is the source's {@code apiUrl}, else {@code settings.apiUrl}, else
 * {@link #DEFAULT_API_URL}.
 *
 * <p>
 * A failed call throws when nothing was fetched yet in the run (bad key, quota refused by RapidAPI,
 * JSearch down — the run reports it); after that it ends the run with what it has, so the calls
 * already paid for are not lost.
 *
 * <p>
 * <strong>Content policy.</strong> JSearch aggregates Google for Jobs. Whether its postings may be
 * republished on indexable pages is unconfirmed, and trabalharei's policy treats it as not allowed.
 * That is a flag for the site to act on (e.g. keep {@code source = jsearch} pages out of the index);
 * this adapter fetches and maps only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class DumJSearchAdapter implements DumJobSourceAdapter {

    public static final String SOURCE_TYPE = "jsearch";
    public static final String RAPIDAPI_HOST = "jsearch.p.rapidapi.com";
    public static final String DEFAULT_API_URL = "https://" + RAPIDAPI_HOST + "/search";
    public static final String KEY_HEADER = "X-RapidAPI-Key";
    public static final String HOST_HEADER = "X-RapidAPI-Host";
    public static final String COUNTRY = "br";
    public static final String LANGUAGE = "pt";

    /**
     * Every JSearch call costs money, so the defaults are deliberately low: 5 a minute, 30 a day, 200 a
     * week, 500 a month. A source overrides any of them to match its RapidAPI plan.
     */
    public static final Map<Window, Integer> DEFAULT_CAPS;

    static {
        Map<Window, Integer> caps = new EnumMap<>(Window.class);
        caps.put(Window.MINUTE, 5);
        caps.put(Window.DAY, 30);
        caps.put(Window.WEEK, 200);
        caps.put(Window.MONTH, 500);
        DEFAULT_CAPS = Map.copyOf(caps);
    }

    private final DumJobFetcher fetcher;

    public DumJSearchAdapter(DumJobFetcher fetcher) {
        this.fetcher = fetcher;
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
        if (isBlank(source.getApiKey())) {
            throw new DumJobFetchException("JSearch source '" + source.getName() + "' needs an apiKey");
        }
        DumJSearchSettings settings = DumJSearchSettings.parse(source.getSettings());
        List<String> queries = settings.queries();
        int slot = DumJobSourceAdapter.rotationSlot(source.getCursor(), queries.size());

        Run run = new Run(context, settings);
        try {
            run.query(queries.get(slot));
        } finally {
            // A paid call was made, even one that failed: the next run moves on to the next query.
            if (run.calls > 0) {
                source.setCursor(String.valueOf((slot + 1) % queries.size()));
            }
        }
        log.info("JSearch source '{}': {} posting(s) from {} paid call(s) for query '{}' ({} non-BR dropped)",
                source.getName(), run.postings.size(), run.calls, queries.get(slot), run.dropped);
        return new ArrayList<>(run.postings.values());
    }

    /** One fetch: the postings gathered so far (by id, first seen wins order) and the calls made. */
    private final class Run {
        private final DumJobFetchContext context;
        private final DumJSearchSettings settings;
        private final Map<String, Map<String, Object>> postings = new LinkedHashMap<>();
        private int calls;
        private int dropped;

        private Run(DumJobFetchContext context, DumJSearchSettings settings) {
            this.context = context;
            this.settings = settings;
        }

        /** Page through one query; every exit is final for the run — nothing is retried. */
        private void query(String query) {
            String name = context.source().getName();
            for (int page = 1; page <= settings.maxPages(); page++) {
                if (!context.budget().tryAcquire()) {
                    logRefusal(query, page);
                    return;
                }
                calls++;
                DumJobRequest request = request(query, page);
                JSONArray data;
                try {
                    data = data(fetcher.send(request), request.secrets());
                } catch (DumJobFetchException e) {
                    if (postings.isEmpty()) {
                        throw e;
                    }
                    log.warn("JSearch source '{}': '{}' page {} failed ({}) — not retried, keeping the {} "
                            + "posting(s) already fetched", name, query, page, e.getMessage(),
                            postings.size());
                    return;
                }
                if (data.isEmpty()) {
                    return;
                }
                if (addPage(data)) {
                    log.debug("JSearch source '{}': '{}' page {} held only already-seen postings", name,
                            query, page);
                    return;
                }
            }
        }

        /** The one line a budget refusal leaves; a spent monthly budget is named as such. */
        private void logRefusal(String query, int page) {
            DumJobCallBudget budget = context.budget();
            String name = context.source().getName();
            OptionalInt month = budget.remaining(Window.MONTH);
            if (month.isPresent() && month.getAsInt() == 0) {
                log.warn("JSearch source '{}': monthly budget spent — the {}-call cap for the last 30 days "
                        + "is reached, so the run stopped before '{}' page {} with {} posting(s); no "
                        + "JSearch call is made until older calls age out", name, monthCap(), query, page,
                        postings.size());
                return;
            }
            List<String> spent = new ArrayList<>();
            for (Window window : List.of(Window.MINUTE, Window.DAY, Window.WEEK)) {
                OptionalInt left = budget.remaining(window);
                if (left.isPresent() && left.getAsInt() == 0) {
                    spent.add(window.name().toLowerCase(Locale.ROOT));
                }
            }
            log.info("JSearch source '{}': call budget exhausted ({} cap) before '{}' page {} — run "
                    + "stopped with {} posting(s)", name, spent.isEmpty() ? "a" : String.join("/", spent),
                    query, page, postings.size());
        }

        private Object monthCap() {
            Integer configured = context.source().getCapPerMonth();
            return configured != null ? configured : DEFAULT_CAPS.get(Window.MONTH);
        }

        /**
         * Map and keep every Brazilian result of a page; answers whether the page held Brazilian
         * postings and an earlier run fetched every one of them.
         */
        private boolean addPage(JSONArray data) {
            boolean allSeen = true;
            int kept = 0;
            for (int i = 0; i < data.length(); i++) {
                JSONObject result = data.optJSONObject(i);
                Map<String, Object> job = result == null ? Map.of()
                        : DumJSearchMapper.map(result, settings.siteUrl());
                Object id = job.get(DumJobsSchema.ID);
                if (id == null) {
                    dropped++;
                    continue;
                }
                kept++;
                allSeen &= context.wasSeen(id.toString());
                postings.putIfAbsent(id.toString(), job);
            }
            return kept > 0 && allSeen;
        }

        private DumJobRequest request(String query, int page) {
            StringJoiner params = new StringJoiner("&");
            param(params, "query", query);
            param(params, "page", String.valueOf(page));
            param(params, "num_pages", "1");
            param(params, "country", COUNTRY);
            param(params, "language", LANGUAGE);
            param(params, "date_posted", settings.datePosted());
            String apiKey = context.source().getApiKey().trim();
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put(KEY_HEADER, apiKey);
            headers.put(HOST_HEADER, RAPIDAPI_HOST);
            return DumJobRequest.get(URI.create(endpoint() + "?" + params), headers, Set.of(apiKey));
        }

        private String endpoint() {
            String configured = context.source().getApiUrl();
            if (isBlank(configured)) {
                configured = settings.apiUrl();
            }
            String base = isBlank(configured) ? DEFAULT_API_URL : configured.trim();
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            return base;
        }
    }

    /**
     * Read {@code {"status": "OK", "data": [...]}}. A body that is not JSON, or a {@code status} other
     * than {@code OK}, is a failed call.
     */
    static JSONArray data(String body, Set<String> secrets) {
        JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (JSONException e) {
            throw new DumJobFetchException(
                    "JSearch answered something that is not a JSON object: " + e.getMessage(), e);
        }
        String status = root.optString("status", "OK");
        if (!"OK".equalsIgnoreCase(status.trim())) {
            JSONObject error = root.optJSONObject("error");
            String detail = error == null ? root.optString("message", "") : error.optString("message", "");
            throw new DumJobFetchException("JSearch answered status " + status
                    + (detail.isBlank() ? "" : ": " + DumJobHttpFetcher.scrub(detail, secrets)));
        }
        JSONArray data = root.optJSONArray("data");
        return data == null ? new JSONArray() : data;
    }

    private static void param(StringJoiner params, String name, String value) {
        params.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
