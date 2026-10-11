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

package com.viglet.dumont.connector.plugin.jobs.source.bne;

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
 * The BNE (Banco Nacional de Empregos) job source (trabalharei <strong>TRA18</strong>), for sources of
 * type {@code bne}, read through the public list endpoint the {@code bne-vagas} WordPress plugin uses:
 * {@code GET https://plugin-api.vagas.bne.com.br/api/v1/Lists/Jobs?Page=&ItemsPerPage=[&State=]}, no
 * key.
 *
 * <p>
 * BNE sends no dates, so there is no newest-first query to stop on. Each run pages the unfiltered
 * list and then, if budget remains, one UF of the rotation ({@link DumBneSettings#states()}, all 27 by
 * default); the source's cursor moves to the next UF only when that query made a call. A query stops
 * at an empty page, past {@code TotalPages}, after {@code maxPages}, at a page whose every posting an
 * earlier run already fetched, or when the call budget refuses (which also ends the run).
 *
 * <p>
 * {@code publishedAt} is the time this source first fetched the posting
 * ({@link DumJobFetchContext#firstSeenAt}), so it stays put from run to run. The list endpoint is the
 * source's {@code apiUrl}, else {@code settings.apiUrl}, else {@link #DEFAULT_API_URL}.
 *
 * <p>
 * A failed call throws when nothing was fetched yet in the run; after that it ends the run with what
 * it has, so the calls already spent are not lost.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class DumBneAdapter implements DumJobSourceAdapter {

    public static final String SOURCE_TYPE = "bne";
    public static final String DEFAULT_API_URL = "https://plugin-api.vagas.bne.com.br/api/v1/Lists/Jobs";

    /**
     * BNE publishes no limits for the plugin endpoint, so the defaults are conservative: 10 a minute,
     * 500 a day, 3,000 a week, 10,000 a month. A source overrides any of them.
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
    private final Clock clock;

    @Autowired
    public DumBneAdapter(DumJobFetcher fetcher) {
        this(fetcher, Clock.systemUTC());
    }

    public DumBneAdapter(DumJobFetcher fetcher, Clock clock) {
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

    /**
     * BNE sends no dates: {@code publishedAt} is when this source first fetched the posting, so a
     * posting expires 30 days after the run that last fetched it (trabalharei <strong>TRA24</strong>).
     */
    @Override
    public boolean datedPostings() {
        return false;
    }

    @Override
    public List<Map<String, Object>> fetch(DumJobFetchContext context) {
        DumJobSource source = context.source();
        DumBneSettings settings = DumBneSettings.parse(source.getSettings());
        Run run = new Run(context, settings, clock.instant());

        boolean refused = run.query(null);
        List<String> states = settings.states();
        int slot = DumJobSourceAdapter.rotationSlot(source.getCursor(), states.size());
        if (!refused) {
            int callsBefore = run.calls;
            run.query(states.get(slot));
            if (run.calls > callsBefore) {
                source.setCursor(String.valueOf((slot + 1) % states.size()));
            }
        }
        log.info("BNE source '{}': {} posting(s) from {} call(s), UF {}", source.getName(),
                run.postings.size(), run.calls, states.get(slot));
        return new ArrayList<>(run.postings.values());
    }

    /** One BNE list page: its entries and, when sent, {@code pagination.TotalPages}. */
    record Page(JSONArray vagas, Integer totalPages) {
    }

    /** One fetch: the postings gathered so far (by id, first seen wins order) and the calls made. */
    private final class Run {
        private final DumJobFetchContext context;
        private final DumBneSettings settings;
        private final Instant now;
        private final Map<String, Map<String, Object>> postings = new LinkedHashMap<>();
        private int calls;

        private Run(DumJobFetchContext context, DumBneSettings settings, Instant now) {
            this.context = context;
            this.settings = settings;
            this.now = now;
        }

        /**
         * Page through one query ({@code state} {@code null} = the unfiltered list). Answers whether
         * it stopped because the budget refused a call.
         */
        private boolean query(String state) {
            String name = context.source().getName();
            String label = state == null ? "all" : "UF " + state;
            for (int page = 1; page <= settings.maxPages(); page++) {
                if (!context.budget().tryAcquire()) {
                    log.info("BNE source '{}': call budget exhausted at {} page {}", name, label, page);
                    context.note("call budget exhausted at %s page %d".formatted(label, page));
                    return true;
                }
                calls++;
                Page result;
                try {
                    result = page(fetcher.get(uri(state, page), Map.of()));
                } catch (DumJobFetchException e) {
                    if (postings.isEmpty()) {
                        throw e;
                    }
                    log.warn("BNE source '{}': {} page {} failed ({}) — keeping the {} posting(s) "
                            + "already fetched", name, label, page, e.getMessage(), postings.size());
                    context.note("%s page %d failed (%s)".formatted(label, page, e.getMessage()));
                    return false;
                }
                if (result.vagas().isEmpty()) {
                    return false;
                }
                if (addPage(result.vagas())) {
                    log.debug("BNE source '{}': {} page {} held only already-seen postings", name,
                            label, page);
                    return false;
                }
                if (result.totalPages() != null && page >= result.totalPages()) {
                    return false;
                }
            }
            return false;
        }

        /**
         * Map and keep every entry of a page; answers whether the page held postings and an earlier
         * run fetched every one of them.
         */
        private boolean addPage(JSONArray vagas) {
            boolean allSeen = true;
            int kept = 0;
            for (int i = 0; i < vagas.length(); i++) {
                JSONObject vaga = vagas.optJSONObject(i);
                String id = vaga == null ? null : DumBneMapper.id(vaga);
                if (id == null) {
                    continue;
                }
                kept++;
                allSeen &= context.wasSeen(id);
                Instant firstSeen = context.firstSeenAt(id);
                postings.putIfAbsent(id, DumBneMapper.map(vaga, settings.siteUrl(),
                        firstSeen != null ? firstSeen : now));
            }
            return kept > 0 && allSeen;
        }

        private URI uri(String state, int page) {
            StringJoiner query = new StringJoiner("&");
            param(query, "Page", String.valueOf(page));
            param(query, "ItemsPerPage", String.valueOf(settings.itemsPerPage()));
            if (state != null) {
                param(query, "State", state);
            }
            return URI.create(endpoint() + "?" + query);
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

    /** Read {@code {"data": {"listVagas": [...], "pagination": {"TotalPages": n}}}}. */
    static Page page(String body) {
        JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (JSONException e) {
            throw new DumJobFetchException(
                    "BNE answered something that is not a JSON object: " + e.getMessage(), e);
        }
        JSONObject data = root.optJSONObject("data");
        JSONArray vagas = data == null ? null : data.optJSONArray("listVagas");
        JSONObject pagination = data == null ? null : data.optJSONObject("pagination");
        Integer totalPages = null;
        if (pagination != null && pagination.has("TotalPages") && !pagination.isNull("TotalPages")) {
            int value = pagination.optInt("TotalPages", -1);
            totalPages = value >= 0 ? value : null;
        }
        return new Page(vagas == null ? new JSONArray() : vagas, totalPages);
    }

    private static void param(StringJoiner query, String name, String value) {
        query.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
