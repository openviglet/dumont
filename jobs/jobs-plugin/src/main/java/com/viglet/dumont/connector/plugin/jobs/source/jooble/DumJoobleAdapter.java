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

package com.viglet.dumont.connector.plugin.jobs.source.jooble;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobCallBudget.Window;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobFetchContext;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobSourceAdapter;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetchException;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetcher;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * The Jooble job source (trabalharei <strong>TRA16</strong>), for sources of type {@code jooble}.
 *
 * <p>
 * Jooble searches need keywords, so there is no unfiltered pass: each run takes one slot of the
 * keyword rotation ({@link DumJoobleSettings#keywords()}) and pages it with
 * {@code POST https://jooble.org/api/{apiKey}} and a JSON body ({@code keywords}, {@code location},
 * {@code page}, and {@code radius}/{@code salary} when set) — or a GET with the same values as query
 * parameters when {@code settings.method} is {@code GET}. Paging stops at the first page that holds
 * a posting an earlier run already fetched, at an empty page, after {@code maxPages}, or when the
 * call budget refuses. The source's cursor moves to the next slot only when a call was made, so a
 * run the budget refuses outright retries the same keyword. Jooble ranks by relevance, not date, so
 * the seen-id stop is a heuristic: it ends paging once a query is back among postings already
 * indexed.
 *
 * <p>
 * The credential is the source's {@code apiKey}; it travels as a path segment, so it is passed to
 * the fetcher as a request secret and masked in every log and error message. {@code apiUrl}, when
 * set, replaces {@code https://jooble.org/api}.
 *
 * <p>
 * A failed call throws when nothing was fetched yet in the run (bad key, Jooble down — the run
 * reports it); after that it ends the run with what it has, so the calls already spent are not lost.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class DumJoobleAdapter implements DumJobSourceAdapter {

    public static final String SOURCE_TYPE = "jooble";
    public static final String DEFAULT_API_URL = "https://jooble.org/api";

    /**
     * Jooble sets limits per key and does not publish them, so the defaults are conservative: 10 a
     * minute, 500 a day, 3,000 a week, 10,000 a month. A source overrides any of them.
     */
    public static final Map<Window, Integer> DEFAULT_CAPS;

    static {
        Map<Window, Integer> caps = new EnumMap<>(Window.class);
        caps.put(Window.MINUTE, 10);
        caps.put(Window.DAY, 500);
        caps.put(Window.WEEK, 3_000);
        caps.put(Window.MONTH, 10_000);
        DEFAULT_CAPS = Map.copyOf(caps);
    }

    private final DumJobFetcher fetcher;

    public DumJoobleAdapter(DumJobFetcher fetcher) {
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

    /**
     * Never HEAD-check a Jooble apply link (trabalharei <strong>TRA24</strong>). Jooble pays per click
     * and its apply links are tracking redirects through {@code jooble.org}: a HEAD request is a hit on
     * that redirect and may be counted as a click, which would bill (or flag) traffic nobody sent. A
     * Jooble posting therefore only expires by its date or when Jooble stops returning it.
     */
    @Override
    public boolean checkApplyLinks() {
        return false;
    }

    @Override
    public List<Map<String, Object>> fetch(DumJobFetchContext context) {
        DumJobSource source = context.source();
        if (isBlank(source.getApiKey())) {
            throw new DumJobFetchException("Jooble source '" + source.getName() + "' needs an apiKey");
        }
        DumJoobleSettings settings = DumJoobleSettings.parse(source.getSettings());
        List<String> keywords = settings.keywords();
        int slot = DumJobSourceAdapter.rotationSlot(source.getCursor(), keywords.size());

        Run run = new Run(context, settings);
        run.query(keywords.get(slot));
        if (run.calls > 0) {
            source.setCursor(String.valueOf((slot + 1) % keywords.size()));
        }
        log.info("Jooble source '{}': {} posting(s) from {} call(s) for keywords '{}'",
                source.getName(), run.postings.size(), run.calls, keywords.get(slot));
        return new ArrayList<>(run.postings.values());
    }

    /** One fetch: the postings gathered so far (by id, first seen wins order) and the calls made. */
    private final class Run {
        private final DumJobFetchContext context;
        private final DumJoobleSettings settings;
        private final Map<String, Map<String, Object>> postings = new LinkedHashMap<>();
        private int calls;

        private Run(DumJobFetchContext context, DumJoobleSettings settings) {
            this.context = context;
            this.settings = settings;
        }

        /** Page through one keyword query. */
        private void query(String keywords) {
            String name = context.source().getName();
            for (int page = 1; page <= settings.maxPages(); page++) {
                if (!context.budget().tryAcquire()) {
                    log.info("Jooble source '{}': call budget exhausted at '{}' page {}", name,
                            keywords, page);
                    return;
                }
                calls++;
                JSONArray jobs;
                try {
                    jobs = jobs(fetcher.send(request(keywords, page)));
                } catch (DumJobFetchException e) {
                    if (postings.isEmpty()) {
                        throw e;
                    }
                    log.warn("Jooble source '{}': '{}' page {} failed ({}) — keeping the {} posting(s) "
                            + "already fetched", name, keywords, page, e.getMessage(), postings.size());
                    return;
                }
                if (jobs.isEmpty()) {
                    return;
                }
                if (addPage(jobs)) {
                    log.debug("Jooble source '{}': '{}' page {} reached already-seen postings", name,
                            keywords, page);
                    return;
                }
            }
        }

        /** Map and keep every result of a page; answers whether any was seen by an earlier run. */
        private boolean addPage(JSONArray jobs) {
            boolean reachedSeen = false;
            for (int i = 0; i < jobs.length(); i++) {
                JSONObject result = jobs.optJSONObject(i);
                Map<String, Object> job = result == null ? Map.of()
                        : DumJoobleMapper.map(result, settings.siteUrl());
                Object id = job.get(DumJobsSchema.ID);
                if (id != null) {
                    reachedSeen |= context.wasSeen(id.toString());
                    postings.putIfAbsent(id.toString(), job);
                }
            }
            return reachedSeen;
        }

        private DumJobRequest request(String keywords, int page) {
            DumJobSource source = context.source();
            String base = isBlank(source.getApiUrl()) ? DEFAULT_API_URL : source.getApiUrl().trim();
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            String apiKey = source.getApiKey().trim();
            String endpoint = base + "/" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            Map<String, String> values = new LinkedHashMap<>();
            values.put("keywords", keywords);
            values.put("location", settings.location());
            values.put("page", String.valueOf(page));
            if (settings.radius() != null) {
                values.put("radius", String.valueOf(settings.radius()));
            }
            if (settings.salary() != null) {
                values.put("salary", String.valueOf(settings.salary()));
            }
            if (DumJobRequest.GET.equals(settings.method())) {
                StringJoiner query = new StringJoiner("&");
                values.forEach((k, v) -> query.add(k + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
                return new DumJobRequest(DumJobRequest.GET, URI.create(endpoint + "?" + query), Map.of(),
                        null, Set.of(apiKey));
            }
            return DumJobRequest.post(URI.create(endpoint), Map.of(), new JSONObject(values).toString(),
                    Set.of(apiKey));
        }

        private JSONArray jobs(String body) {
            try {
                JSONArray jobs = new JSONObject(body).optJSONArray("jobs");
                return jobs == null ? new JSONArray() : jobs;
            } catch (JSONException e) {
                throw new DumJobFetchException(
                        "Jooble answered something that is not a JSON object: " + e.getMessage(), e);
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
