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

package com.viglet.dumont.connector.commons.spi.structured.jsonapi;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumJsonApiManifest.FieldMapping;
import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumJsonApiManifest.PagingMode;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link DumStructuredSource} that runs a {@link DumJsonApiManifest} (roadmap
 * <strong>D92</strong>): {@link #discover()} walks the API's pages once, keeping every record, and
 * {@link #extract(String)} maps one of them through the manifest's field paths.
 *
 * <p>
 * <strong>When the walk ends.</strong> An empty page, a page shorter than the declared size, a
 * total count reached, a cursor that is blank or repeats, and a page holding only ids already seen
 * each end the walk <em>complete</em>. A page cap, the run's call cap, or a failed call end it
 * <em>incomplete</em>: {@link #discoveryComplete()} then answers {@code false}, and the plugin
 * finishes the run standalone so the records this run never reached are not deindexed.
 *
 * <p>
 * <strong>Budgets and increments.</strong> Day, week and month allowances are counted in the
 * source's {@link DumJsonApiState} across runs, and a spent window stops the walk like the run cap.
 * An incremental manifest marks every id it found as seen; the plugin then never sweeps, and
 * {@link #expiredIds()} names the ids unseen for longer than the expiry.
 *
 * <p>
 * A value whose type does not fit its field is dropped, not coerced — the same grounding contract
 * every structured source keeps.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumJsonApiSource implements DumStructuredSource {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z]+)(?::([^{}]+))?}");

    private final DumJsonApiManifest manifest;
    private final DumStructuredSchema schema;
    private final String sourceName;
    private final String providerName;
    private final List<String> siteNames;
    private final Locale locale;
    private final Map<String, String> secrets;
    private final DumJsonFetcher fetcher;
    private final Pacer pacer;
    private final DumJsonApiState state;
    private final Clock clock;
    private final DumJsonPath recordsPath;
    private final DumJsonPath idPath;
    private final Map<String, DumJsonPath> fieldPaths = new LinkedHashMap<>();

    private Map<String, JSONObject> recordsById;
    private boolean complete;
    private int calls;

    private DumJsonApiSource(Builder builder) {
        this.manifest = builder.manifest;
        this.schema = manifest.schema();
        this.sourceName = builder.sourceName;
        this.providerName = builder.providerName;
        this.siteNames = List.copyOf(builder.siteNames);
        this.locale = builder.locale;
        this.secrets = Map.copyOf(builder.secrets);
        this.fetcher = builder.fetcher;
        this.pacer = builder.pacer;
        this.state = builder.state;
        this.clock = builder.clock;
        this.recordsPath = DumJsonPath.compile(manifest.records());
        this.idPath = DumJsonPath.compile(manifest.idPath());
        manifest.fields().forEach(f -> fieldPaths.put(f.name(), DumJsonPath.compile(f.path())));
    }

    public static Builder builder(DumJsonApiManifest manifest, DumJsonFetcher fetcher) {
        return new Builder(manifest, fetcher);
    }

    @Override
    public String getSourceName() {
        return sourceName;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public Collection<String> getSiteNames() {
        return siteNames;
    }

    @Override
    public DumStructuredSchema schema() {
        return schema;
    }

    @Override
    public Locale getLocale() {
        return locale;
    }

    @Override
    public List<String> discover() {
        return new ArrayList<>(records().keySet());
    }

    @Override
    public synchronized boolean discoveryComplete() {
        records();
        return complete;
    }

    /** How many calls the walk made. */
    public synchronized int calls() {
        records();
        return calls;
    }

    @Override
    public Optional<DumStructuredRecord> extract(String contentId) {
        JSONObject json = records().get(contentId);
        if (json == null) {
            return Optional.empty();
        }
        DumStructuredRecord.Builder rec = DumStructuredRecord.builder(contentId, schema);
        for (FieldMapping field : manifest.fields()) {
            Object value = toFieldValue(fieldPaths.get(field.name()).read(json), field.multiValued());
            try {
                rec.set(field.name(), value, DumFieldProvenance.json(field.path()));
            } catch (IllegalArgumentException e) {
                log.debug("Dropping '{}' of record '{}' in source '{}': {}", field.name(), contentId,
                        sourceName, e.getMessage());
            }
        }
        return Optional.of(rec.build());
    }

    @Override
    public boolean incremental() {
        return manifest.incremental();
    }

    /**
     * The ids not seen for longer than the manifest's expiry, forgotten as they are returned. Empty
     * for a source that is not incremental, and for a walk that failed before it found anything —
     * that run learned nothing about what is gone.
     */
    @Override
    public synchronized List<String> expiredIds() {
        if (!manifest.incremental()) {
            return List.of();
        }
        if (records().isEmpty() && !complete) {
            return List.of();
        }
        return state.expire(clock.instant().minus(manifest.expiry()));
    }

    private synchronized Map<String, JSONObject> records() {
        if (recordsById == null) {
            recordsById = walk();
            if (manifest.incremental() && !recordsById.isEmpty()) {
                state.markSeen(recordsById.keySet(), clock.instant());
            }
        }
        return recordsById;
    }

    /** The first budget window with nothing left, or {@code null} when every one has room. */
    private DumJsonApiState.Window spentWindow() {
        Instant now = clock.instant();
        for (Map.Entry<DumJsonApiState.Window, Integer> window : manifest.budget().windows().entrySet()) {
            if (state.calls(window.getKey(), window.getKey().start(now)) >= window.getValue()) {
                return window.getKey();
            }
        }
        return null;
    }

    private Map<String, JSONObject> walk() {
        Map<String, JSONObject> found = new LinkedHashMap<>();
        DumJsonApiManifest.Paging paging = manifest.paging();
        int maxCalls = manifest.budget().maxCallsPerRun();
        Set<String> cursors = new HashSet<>();
        String cursor = "";
        for (int step = 0;; step++) {
            if (maxCalls > 0 && calls >= maxCalls) {
                log.info("JSON API source '{}' stopped at its call cap of {}", sourceName, maxCalls);
                complete = false;
                return found;
            }
            DumJsonApiState.Window spent = spentWindow();
            if (spent != null) {
                log.info("JSON API source '{}' stopped: its {} allowance of {} calls is spent", sourceName,
                        spent.name().toLowerCase(Locale.ROOT), manifest.budget().windows().get(spent));
                complete = false;
                return found;
            }
            if (paging.maxPages() > 0 && step >= paging.maxPages()) {
                log.info("JSON API source '{}' stopped at its page cap of {}", sourceName,
                        paging.maxPages());
                complete = false;
                return found;
            }
            Object response = call(step, cursor);
            if (response == null) {
                complete = false;
                return found;
            }
            List<JSONObject> page = recordsOf(response);
            int added = 0;
            for (JSONObject json : page) {
                String id = idOf(json);
                if (id == null) {
                    log.warn("JSON API source '{}' skipped a record with no id at '{}'", sourceName,
                            manifest.idPath());
                } else if (found.putIfAbsent(id, json) == null) {
                    added++;
                }
            }
            if (endsComplete(response, page, added, found.size())) {
                complete = true;
                return found;
            }
            if (paging.mode() == PagingMode.CURSOR) {
                cursor = text(DumJsonPath.compile(paging.cursorPath()).read(response));
                if (cursor == null || !cursors.add(cursor)) {
                    complete = true;
                    return found;
                }
            }
        }
    }

    private boolean endsComplete(Object response, List<JSONObject> page, int added, int total) {
        DumJsonApiManifest.Paging paging = manifest.paging();
        if (paging.mode() == PagingMode.NONE || page.isEmpty() || added == 0) {
            return true;
        }
        if (paging.size() > 0 && page.size() < paging.size()) {
            return true;
        }
        if (paging.totalPath() != null) {
            String declared = text(DumJsonPath.compile(paging.totalPath()).read(response));
            if (declared != null) {
                try {
                    return total >= Long.parseLong(declared);
                } catch (NumberFormatException e) {
                    log.debug("JSON API source '{}' read a non-numeric total '{}'", sourceName, declared);
                }
            }
        }
        return false;
    }

    private Object call(int step, String cursor) {
        DumJsonApiRequest request = resolve(step, cursor);
        pacer.awaitTurn(manifest.budget().minIntervalMs());
        calls++;
        // Counted before the answer: a call that failed was still spent against the provider's quota.
        Instant now = clock.instant();
        manifest.budget().windows().keySet().forEach(window -> state.addCall(window, window.start(now)));
        String body;
        try {
            body = fetcher.fetch(request);
        } catch (IOException e) {
            log.warn("JSON API source '{}' call {} failed: {}", sourceName, calls, e.getMessage());
            return null;
        }
        try {
            return new JSONTokener(body == null ? "" : body).nextValue();
        } catch (JSONException e) {
            log.warn("JSON API source '{}' call {} returned no JSON: {}", sourceName, calls,
                    e.getMessage());
            return null;
        }
    }

    private List<JSONObject> recordsOf(Object response) {
        Object node = recordsPath.read(response);
        List<JSONObject> page = new ArrayList<>();
        if (node instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                if (array.opt(i) instanceof JSONObject json) {
                    page.add(json);
                }
            }
        } else if (node instanceof List<?> list) {
            list.stream().filter(JSONObject.class::isInstance).map(JSONObject.class::cast)
                    .forEach(page::add);
        } else if (node instanceof JSONObject json) {
            page.add(json);
        }
        return page;
    }

    private String idOf(JSONObject json) {
        Object id = idPath.read(json);
        if (id instanceof List<?> list) {
            id = list.isEmpty() ? null : list.getFirst();
        }
        return text(id);
    }

    DumJsonApiRequest resolve(int step, String cursor) {
        DumJsonApiManifest.Paging paging = manifest.paging();
        int page = (paging.mode() == PagingMode.PAGE ? paging.start() : 1) + step;
        long offset = (paging.mode() == PagingMode.OFFSET ? paging.start() : 0)
                + (long) step * paging.size();
        Map<String, String> values = Map.of("page", String.valueOf(page), "offset",
                String.valueOf(offset), "size", String.valueOf(paging.size()), "cursor", cursor);
        DumJsonApiManifest.Request template = manifest.request();
        Map<String, String> headers = new LinkedHashMap<>();
        template.headers().forEach((k, v) -> headers.put(k, fill(v, values, Encoding.NONE)));
        return new DumJsonApiRequest(template.method(), fill(template.url(), values, Encoding.URL),
                headers, template.body() == null ? null : fill(template.body(), values, Encoding.JSON));
    }

    private String fill(String template, Map<String, String> values, Encoding encoding) {
        String text = template;
        if (encoding == Encoding.JSON) {
            // A numeric placeholder that is a whole JSON string ("{page}") becomes a bare number, so
            // a manifest body stays valid JSON and the API still receives a number.
            for (String numeric : List.of("page", "offset", "size")) {
                text = text.replace("\"{" + numeric + "}\"", values.get(numeric));
            }
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String variable = matcher.group(1);
            String value = "secret".equals(variable) ? secrets.get(matcher.group(2).trim())
                    : values.get(variable);
            matcher.appendReplacement(out, Matcher.quoteReplacement(encoding.apply(value)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static Object toFieldValue(Object value, boolean multiValued) {
        if (value instanceof JSONArray array) {
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item != null && !JSONObject.NULL.equals(item)) {
                    list.add(item instanceof JSONObject || item instanceof JSONArray ? item.toString() : item);
                }
            }
            value = list;
        } else if (value instanceof JSONObject json) {
            value = json.toString();
        }
        if (value instanceof List<?> list) {
            List<Object> flat = list.stream()
                    .map(v -> v instanceof JSONObject || v instanceof JSONArray ? v.toString() : v)
                    .map(Object.class::cast).toList();
            if (multiValued) {
                return flat;
            }
            return flat.isEmpty() ? null : flat.getFirst();
        }
        return multiValued && value != null ? List.of(value) : value;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /** How a resolved value is written into its template. */
    private enum Encoding {
        NONE, URL, JSON;

        String apply(String value) {
            String raw = value == null ? "" : value;
            return switch (this) {
                case NONE -> raw;
                case URL -> URLEncoder.encode(raw, StandardCharsets.UTF_8);
                case JSON -> {
                    // Escaped as string content: the placeholder sits inside a JSON string.
                    String quoted = JSONObject.quote(raw);
                    yield quoted.substring(1, quoted.length() - 1);
                }
            };
        }
    }

    /** Spaces calls so no minute holds more than the manifest allows. */
    @FunctionalInterface
    public interface Pacer {
        /** Blocks until a call spaced {@code minIntervalMs} after the previous one may go. */
        void awaitTurn(long minIntervalMs);

        /** A pacer over the wall clock that sleeps the remainder of the interval. */
        static Pacer sleeping() {
            return sleeping(System::currentTimeMillis, ms -> {
                try {
                    Thread.sleep(ms);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        /** A pacer over a given clock and sleep, so a test can run it without waiting. */
        static Pacer sleeping(LongSupplier clock, java.util.function.LongConsumer sleep) {
            long[] last = {Long.MIN_VALUE};
            return minIntervalMs -> {
                long now = clock.getAsLong();
                if (last[0] != Long.MIN_VALUE && minIntervalMs > 0) {
                    long wait = last[0] + minIntervalMs - now;
                    if (wait > 0) {
                        sleep.accept(wait);
                        now += wait;
                    }
                }
                last[0] = now;
            };
        }
    }

    public static final class Builder {
        private final DumJsonApiManifest manifest;
        private final DumJsonFetcher fetcher;
        private String sourceName;
        private String providerName = "JSON-API";
        private Collection<String> siteNames = List.of();
        private Locale locale = Locale.US;
        private Map<String, String> secrets = Map.of();
        private Pacer pacer = Pacer.sleeping();
        private DumJsonApiState state = new DumInMemoryJsonApiState();
        private Clock clock = Clock.systemUTC();

        private Builder(DumJsonApiManifest manifest, DumJsonFetcher fetcher) {
            if (manifest == null) {
                throw new IllegalArgumentException("A manifest is required");
            }
            if (fetcher == null) {
                throw new IllegalArgumentException("A fetcher is required");
            }
            this.manifest = manifest;
            this.fetcher = fetcher;
        }

        public Builder sourceName(String sourceName) {
            this.sourceName = sourceName;
            return this;
        }

        public Builder providerName(String providerName) {
            this.providerName = providerName;
            return this;
        }

        public Builder siteNames(Collection<String> siteNames) {
            this.siteNames = siteNames == null ? List.of() : siteNames;
            return this;
        }

        public Builder locale(Locale locale) {
            this.locale = locale == null ? Locale.US : locale;
            return this;
        }

        /** The values the manifest's {@code {secret:NAME}} placeholders resolve to. */
        public Builder secrets(Map<String, String> secrets) {
            this.secrets = secrets == null ? Map.of() : secrets;
            return this;
        }

        public Builder pacer(Pacer pacer) {
            this.pacer = pacer == null ? Pacer.sleeping() : pacer;
            return this;
        }

        /**
         * What the source remembers between runs: budget windows and last-seen ids. Defaults to
         * memory, which a restart forgets.
         */
        public Builder state(DumJsonApiState state) {
            this.state = state == null ? new DumInMemoryJsonApiState() : state;
            return this;
        }

        /** The clock budget windows and expiry are read against. */
        public Builder clock(Clock clock) {
            this.clock = clock == null ? Clock.systemUTC() : clock;
            return this;
        }

        /**
         * @throws IllegalArgumentException when the source has no name or a secret the manifest
         *                                  asks for was not given
         */
        public DumJsonApiSource build() {
            if (sourceName == null || sourceName.isBlank()) {
                throw new IllegalArgumentException("A source name is required");
            }
            List<String> missing = manifest.secretNames().stream()
                    .filter(s -> secrets.get(s) == null || secrets.get(s).isBlank()).toList();
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Source '%s' is missing the secret(s) %s that manifest '%s' uses"
                        .formatted(sourceName, missing, manifest.name()));
            }
            return new DumJsonApiSource(this);
        }
    }
}
