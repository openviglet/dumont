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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.dumont.connector.commons.spi.structured.DumEmbeddingRole;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * A paged JSON API declared as data rather than code (roadmap <strong>D92</strong>): the request,
 * how it pages and when it stops, how many calls a run may spend, where the records sit and how
 * each field maps. {@link #parse(String)} reads it from JSON and refuses anything it cannot run, so
 * a bad manifest fails when it is loaded and not halfway through a crawl.
 *
 * <pre>{@code
 * {
 *   "name": "adzuna", "version": 1,
 *   "request": { "method": "GET",
 *     "url": "https://api.adzuna.com/v1/api/jobs/br/search/{page}?app_id={secret:appId}&app_key={secret:appKey}&results_per_page={size}" },
 *   "paging": { "mode": "page", "start": 1, "size": 50, "maxPages": 20, "totalPath": "count" },
 *   "budget": { "maxCallsPerRun": 20, "callsPerMinute": 25 },
 *   "records": "results",
 *   "id": "id",
 *   "fields": [
 *     { "name": "title", "path": "title", "type": "TEXT", "role": "TITLE", "mandatory": true },
 *     { "name": "company", "path": "company.display_name", "facet": true },
 *     { "name": "url", "path": "redirect_url", "mandatory": true }
 *   ]
 * }
 * }</pre>
 *
 * <p>
 * Placeholders, in the URL, the headers and the body: {@code {page}}, {@code {offset}},
 * {@code {size}}, {@code {cursor}} and {@code {secret:NAME}}. Secrets are never written into the
 * manifest: the source is handed them when it is bound, so a manifest can be shared and versioned.
 *
 * @param name    the manifest name (required)
 * @param version a positive version
 * @param request the call template
 * @param paging  how successive calls differ and when the walk ends
 * @param budget  what one run may spend
 * @param records the path to the record array in each response ({@code $} for a bare array)
 * @param idPath  the path to each record's id, relative to the record
 * @param fields  the field mappings, in schema order
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumJsonApiManifest(String name, int version, Request request, Paging paging,
        Budget budget, String records, String idPath, List<FieldMapping> fields) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z]+)(?::([^{}]+))?}");
    private static final Set<String> VARIABLES = Set.of("page", "offset", "size", "cursor", "secret");

    public DumJsonApiManifest {
        require(name != null && !name.isBlank(), "a manifest needs a name");
        require(version > 0, "manifest '%s' needs a positive version".formatted(name));
        require(request != null, "manifest '%s' needs a request".formatted(name));
        paging = paging == null ? Paging.NONE : paging;
        budget = budget == null ? Budget.UNLIMITED : budget;
        records = records == null || records.isBlank() ? "$" : records.trim();
        require(idPath != null && !idPath.isBlank(), "manifest '%s' needs an id path".formatted(name));
        require(fields != null && !fields.isEmpty(),
                "manifest '%s' needs at least one field".formatted(name));
        fields = List.copyOf(fields);
        Set<String> names = new LinkedHashSet<>();
        fields.forEach(f -> require(names.add(f.name()),
                "manifest '%s' maps field '%s' twice".formatted(name, f.name())));
        require(paging.mode() != PagingMode.CURSOR || paging.cursorPath() != null,
                "manifest '%s' pages by cursor but declares no cursorPath".formatted(name));
        require(paging.mode() != PagingMode.CURSOR || request.mentions("cursor"),
                "manifest '%s' pages by cursor but its request never uses {cursor}".formatted(name));
        require(paging.mode() != PagingMode.PAGE || request.mentions("page"),
                "manifest '%s' pages by page but its request never uses {page}".formatted(name));
        require(paging.mode() != PagingMode.OFFSET || request.mentions("offset"),
                "manifest '%s' pages by offset but its request never uses {offset}".formatted(name));
        require(paging.mode() != PagingMode.OFFSET || paging.size() > 0,
                "manifest '%s' pages by offset but declares no page size".formatted(name));
        DumJsonPath.compile(records);
        DumJsonPath.compile(idPath);
        if (paging.cursorPath() != null) {
            DumJsonPath.compile(paging.cursorPath());
        }
        if (paging.totalPath() != null) {
            DumJsonPath.compile(paging.totalPath());
        }
    }

    /**
     * Reads a manifest from its JSON form.
     *
     * @throws IllegalArgumentException naming what is wrong, when the JSON is malformed or the
     *                                  manifest cannot run
     */
    public static DumJsonApiManifest parse(String json) {
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException e) {
            throw new IllegalArgumentException("A JSON API manifest must be a JSON object: "
                    + e.getMessage(), e);
        }
        try {
            List<FieldMapping> fields = new ArrayList<>();
            JSONArray fieldArray = root.optJSONArray("fields");
            if (fieldArray != null) {
                for (int i = 0; i < fieldArray.length(); i++) {
                    fields.add(FieldMapping.parse(fieldArray.getJSONObject(i)));
                }
            }
            return new DumJsonApiManifest(root.optString("name", null), root.optInt("version", 1),
                    Request.parse(root.optJSONObject("request")),
                    Paging.parse(root.optJSONObject("paging")),
                    Budget.parse(root.optJSONObject("budget")),
                    root.optString("records", "$"), root.optString("id", null), fields);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid JSON API manifest: " + e.getMessage(), e);
        }
    }

    /** The schema the mapped fields declare, in manifest order. */
    public DumStructuredSchema schema() {
        DumStructuredSchema.Builder builder = DumStructuredSchema.builder();
        fields.forEach(f -> builder.field(f.toField()));
        return builder.build();
    }

    /** The names every {@code {secret:NAME}} placeholder asks for. */
    public Set<String> secretNames() {
        return request.secretNames();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /** How successive calls differ. */
    public enum PagingMode {
        /** One call. */
        NONE,
        /** {@code {page}} counts up from {@code start}. */
        PAGE,
        /** {@code {offset}} counts up from {@code start} in steps of {@code size}. */
        OFFSET,
        /** {@code {cursor}} is the value the previous response held at {@code cursorPath}. */
        CURSOR
    }

    /**
     * The call template.
     *
     * @param method  {@code GET} or {@code POST}
     * @param url     the URL template
     * @param headers header templates
     * @param body    the body template of a {@code POST}, or {@code null}
     */
    public record Request(String method, String url, Map<String, String> headers, String body) {

        public Request {
            method = method == null || method.isBlank() ? "GET" : method.trim().toUpperCase(Locale.ROOT);
            require("GET".equals(method) || "POST".equals(method),
                    "a request method is GET or POST, not " + method);
            require(url != null && !url.isBlank(), "a request needs a url");
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            require(body == null || "POST".equals(method), "only a POST request carries a body");
            // A compact constructor's fields are assigned after its body, so read the parameters.
            for (Placeholder p : placeholders(url, headers, body)) {
                require(VARIABLES.contains(p.variable),
                        "unknown placeholder {%s} in the request".formatted(p.variable));
                require(!"secret".equals(p.variable) || p.argument != null,
                        "a {secret} placeholder names its secret: {secret:NAME}");
            }
        }

        static Request parse(JSONObject json) {
            if (json == null) {
                return null;
            }
            Map<String, String> headers = new LinkedHashMap<>();
            JSONObject headerJson = json.optJSONObject("headers");
            if (headerJson != null) {
                headerJson.keySet().forEach(k -> headers.put(k, headerJson.getString(k)));
            }
            Object body = json.opt("body");
            String bodyText = body == null || JSONObject.NULL.equals(body) ? null : body.toString();
            return new Request(json.optString("method", "GET"), json.optString("url", null), headers,
                    bodyText);
        }

        boolean mentions(String variable) {
            return placeholders(url, headers, body).stream().anyMatch(p -> p.variable.equals(variable));
        }

        Set<String> secretNames() {
            Set<String> names = new LinkedHashSet<>();
            placeholders(url, headers, body).stream().filter(p -> "secret".equals(p.variable))
                    .forEach(p -> names.add(p.argument.trim()));
            return names;
        }

        private static List<Placeholder> placeholders(String url, Map<String, String> headers,
                String body) {
            List<Placeholder> found = new ArrayList<>();
            List<String> templates = new ArrayList<>(headers.values());
            templates.add(url);
            if (body != null) {
                templates.add(body);
            }
            for (String template : templates) {
                Matcher matcher = PLACEHOLDER.matcher(template);
                while (matcher.find()) {
                    found.add(new Placeholder(matcher.group(1), matcher.group(2)));
                }
            }
            return found;
        }
    }

    private record Placeholder(String variable, String argument) {
    }

    /**
     * How the walk pages and when it ends.
     *
     * @param mode       the paging mode
     * @param start      the first page or offset ({@code 1} for pages, {@code 0} for offsets when
     *                   unset)
     * @param size       records per call, or {@code 0} when the API decides; a call returning fewer
     *                   than a declared size is the last
     * @param maxPages   the most calls the walk may make, or {@code 0} for no cap; a walk the cap
     *                   cuts short is incomplete
     * @param cursorPath where the next cursor sits in a response
     * @param totalPath  where the total record count sits in a response, or {@code null}
     */
    public record Paging(PagingMode mode, int start, int size, int maxPages, String cursorPath,
            String totalPath) {

        public static final Paging NONE = new Paging(PagingMode.NONE, 0, 0, 0, null, null);

        public Paging {
            mode = mode == null ? PagingMode.NONE : mode;
            require(size >= 0, "a page size cannot be negative");
            require(maxPages >= 0, "a page cap cannot be negative");
            cursorPath = cursorPath == null || cursorPath.isBlank() ? null : cursorPath.trim();
            totalPath = totalPath == null || totalPath.isBlank() ? null : totalPath.trim();
        }

        static Paging parse(JSONObject json) {
            if (json == null) {
                return NONE;
            }
            String modeName = json.optString("mode", "none").toUpperCase(Locale.ROOT);
            PagingMode mode;
            try {
                mode = PagingMode.valueOf(modeName);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown paging mode '%s'; one of none, page, offset, cursor"
                        .formatted(modeName.toLowerCase(Locale.ROOT)), e);
            }
            int defaultStart = mode == PagingMode.PAGE ? 1 : 0;
            return new Paging(mode, json.optInt("start", defaultStart), json.optInt("size", 0),
                    json.optInt("maxPages", 0), json.optString("cursorPath", null),
                    json.optString("totalPath", null));
        }
    }

    /**
     * What one run may spend.
     *
     * @param maxCallsPerRun the most calls one run makes, or {@code 0} for no cap; a run the cap
     *                       stops is incomplete, so the sweep keeps what it did not fetch
     * @param callsPerMinute the pace, or {@code 0} for none: calls are spaced so no minute holds more
     */
    public record Budget(int maxCallsPerRun, int callsPerMinute) {

        public static final Budget UNLIMITED = new Budget(0, 0);

        public Budget {
            require(maxCallsPerRun >= 0, "a call cap cannot be negative");
            require(callsPerMinute >= 0, "a call pace cannot be negative");
        }

        static Budget parse(JSONObject json) {
            return json == null ? UNLIMITED
                    : new Budget(json.optInt("maxCallsPerRun", 0), json.optInt("callsPerMinute", 0));
        }

        /** The least time between two calls, in milliseconds. */
        long minIntervalMs() {
            return callsPerMinute == 0 ? 0 : (60_000L + callsPerMinute - 1) / callsPerMinute;
        }
    }

    /**
     * One mapped field.
     *
     * @param name        the field name in the indexed document
     * @param path        where the value sits in a record (defaults to the name)
     * @param type        the search-engine type
     * @param multiValued whether the field holds a list
     * @param facet       whether the field is a facet
     * @param mandatory   whether a record without it is skipped
     * @param role        the field's embedding role
     */
    public record FieldMapping(String name, String path, TurSEFieldType type, boolean multiValued,
            boolean facet, boolean mandatory, DumEmbeddingRole role) {

        public FieldMapping {
            require(name != null && !name.isBlank(), "a mapped field needs a name");
            path = path == null || path.isBlank() ? name : path.trim();
            type = type == null ? TurSEFieldType.STRING : type;
            role = role == null ? DumEmbeddingRole.NONE : role;
            DumJsonPath.compile(path);
        }

        static FieldMapping parse(JSONObject json) {
            String type = json.optString("type", "STRING").toUpperCase(Locale.ROOT);
            String role = json.optString("role", "NONE").toUpperCase(Locale.ROOT);
            try {
                return new FieldMapping(json.optString("name", null), json.optString("path", null),
                        TurSEFieldType.valueOf(type), json.optBoolean("multiValued", false),
                        json.optBoolean("facet", false), json.optBoolean("mandatory", false),
                        DumEmbeddingRole.valueOf(role));
            } catch (IllegalArgumentException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("No enum constant")) {
                    throw new IllegalArgumentException("field '%s' has an unknown type '%s' or role '%s'"
                            .formatted(json.optString("name"), type, role), e);
                }
                throw e;
            }
        }

        DumStructuredField toField() {
            return DumStructuredField.builder(name).type(type).multiValued(multiValued).facet(facet)
                    .mandatory(mandatory).embeddingRole(role).build();
        }
    }
}
