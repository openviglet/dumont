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

package com.viglet.dumont.connector.commons.spi.structured.oaipmh;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.dumont.connector.commons.spi.structured.DumEmbeddingRole;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;
import com.viglet.turing.commons.se.field.TurSEFieldType;

import lombok.extern.slf4j.Slf4j;

/**
 * The DSpace profile of an OAI-PMH source (roadmap <strong>D314</strong>): the same harvest, read in
 * DSpace's native {@code dim} format and completed from the repository's REST API, rather than a
 * second harvester.
 *
 * <p>
 * <strong>Metadata.</strong> Each record's {@code <dim:field>} values map onto {@link #SCHEMA}:
 * {@code dc.contributor.author} (and {@code dc.creator}) to {@code authors},
 * {@code dc.contributor.advisor*} — {@code advisor1} and {@code advisor-co1} included, as Brazilian
 * repositories write them — to {@code advisors}, {@code dc.date.issued} to {@code date},
 * {@code dc.description.abstract} to {@code text}, and the handle in {@code dc.identifier.uri} to
 * {@code url}. Nothing is invented: an absent value stays absent.
 *
 * <p>
 * <strong>Community and collection.</strong> A record's {@code com_…} and {@code col_…} set specs
 * become the community and collection <em>names</em>, read once per run from
 * {@code {rest}/core/communities} and {@code {rest}/core/collections}, page by page. A set whose name
 * is not found is left out rather than indexed as its spec.
 *
 * <p>
 * <strong>The item in REST.</strong> Found by handle through {@code {rest}/pid/find}. An item REST
 * answers 401/403 for, or reports withdrawn or not discoverable, is never indexed: it is reported
 * gone. Its bundles give the {@code THUMBNAIL}'s first bitstream as {@code image} and, with full
 * text on, the {@code TEXT} bundle's extracted text, else an {@code ORIGINAL} PDF, appended to
 * {@code text}. A bitstream the public may not read adds no text; the item is still indexed. Every
 * REST URL is built from the configured base — never assumed to be {@code /server/api} — and stays
 * on its host.
 *
 * <p>
 * <strong>Site key.</strong> Every record carries {@code site_key}: the configured one, else the
 * OAI-PMH base URL's host.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumDSpaceProfile {

    /** DSpace's native metadata format, the one this profile harvests. */
    public static final String METADATA_PREFIX = "dim";

    public static final String ADVISORS = "advisors";
    public static final String COMMUNITY = "community";
    public static final String COLLECTION = "collection";
    public static final String IMAGE = "image";
    public static final String SITE_KEY = "site_key";

    /** The schema a source with the DSpace profile publishes. */
    public static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .field(DumStructuredField.builder(DumOaiPmhSource.TITLE).type(TurSEFieldType.TEXT).mandatory(true)
                    .embeddingRole(DumEmbeddingRole.TITLE).description("dc.title").build())
            .field(DumStructuredField.builder(DumOaiPmhSource.TEXT).type(TurSEFieldType.TEXT)
                    .embeddingRole(DumEmbeddingRole.BODY)
                    .description("dc.description.abstract; then the TEXT bundle or a PDF when full text is on")
                    .build())
            .field(DumStructuredField.builder(DumOaiPmhSource.AUTHORS).multiValued(true).facet(true)
                    .description("dc.contributor.author, dc.creator").build())
            .field(DumStructuredField.builder(ADVISORS).multiValued(true).facet(true)
                    .description("dc.contributor.advisor*").build())
            .field(DumStructuredField.builder(DumOaiPmhSource.SUBJECTS).multiValued(true).facet(true)
                    .description("dc.subject, every qualifier").build())
            .field(DumStructuredField.builder(DumOaiPmhSource.DATE).type(TurSEFieldType.DATE)
                    .description("dc.date.issued, when it reads as a date").build())
            .field(DumStructuredField.builder(DumOaiPmhSource.DATE_TEXT)
                    .description("dc.date.issued as written, when it does not").build())
            .field(DumStructuredField.builder(DumOaiPmhSource.TYPE).multiValued(true).facet(true)
                    .description("dc.type").build())
            .field(DumStructuredField.builder(DumOaiPmhSource.PUBLISHER).multiValued(true).facet(true)
                    .description("dc.publisher").build())
            .field(DumStructuredField.builder(DumOaiPmhSource.LANGUAGE).multiValued(true).facet(true)
                    .description("dc.language.iso, dc.language").build())
            .field(DumStructuredField.builder(DumOaiPmhSource.URL)
                    .description("The handle in dc.identifier.uri").build())
            .field(DumStructuredField.builder(DumOaiPmhSource.RELATIONS).multiValued(true)
                    .description("dc.relation, every qualifier").build())
            .field(DumStructuredField.builder(COMMUNITY).multiValued(true).facet(true)
                    .description("The names of the record's com_ sets").build())
            .field(DumStructuredField.builder(COLLECTION).multiValued(true).facet(true)
                    .description("The names of the record's col_ sets").build())
            .field(DumStructuredField.builder(IMAGE)
                    .description("The THUMBNAIL bundle's first bitstream").build())
            .field(DumStructuredField.builder(SITE_KEY).facet(true)
                    .description("The source's site key").build())
            .build();

    /** The most {@code ORIGINAL} PDFs one item's text is looked for in. */
    static final int MAX_PDF_PROBES = 3;
    /** The page size community and collection names are read with. */
    static final int SET_PAGE_SIZE = 100;
    /** The most pages of communities, or of collections, one run reads. */
    static final int MAX_SET_PAGES = 50;

    private static final String DC = "dc";
    private static final String COMMUNITY_SET = "com_";
    private static final String COLLECTION_SET = "col_";

    private final String restUrl;
    private final String restHost;
    private final DumDSpaceRest rest;
    private final boolean fullText;
    private final String siteKey;

    private final List<String> notes = Collections.synchronizedList(new ArrayList<>());
    private Map<String, String> setNames;

    private DumDSpaceProfile(Builder builder) {
        this.restUrl = builder.restUrl;
        this.restHost = URI.create(builder.restUrl).getHost();
        this.rest = builder.rest;
        this.fullText = builder.fullText;
        this.siteKey = builder.siteKey;
    }

    /**
     * @param restUrl the REST API base, such as {@code https://repo.example/server/api}; required,
     *                since a DSpace install may serve it under any path
     */
    public static Builder builder(String restUrl, DumDSpaceRest rest) {
        return new Builder(restUrl, rest);
    }

    /** The configured site key, empty when the source's host stands in. */
    public Optional<String> siteKey() {
        return Optional.ofNullable(siteKey);
    }

    /** What went wrong in REST that a run should say: names it could not read. */
    List<String> notes() {
        synchronized (notes) {
            return List.copyOf(notes);
        }
    }

    /** One item's outcome: the record to index, or the item is gone and must not be. */
    record Outcome(Optional<DumStructuredRecord> record, boolean gone) {

        static Outcome goneItem() {
            return new Outcome(Optional.empty(), true);
        }
    }

    /**
     * Maps one {@code dim} record and completes it from REST.
     *
     * @param defaultSiteKey the site key when none is configured: the OAI-PMH base URL's host
     */
    Outcome extract(String contentId, DumOaiPmhResponse.Item item, String defaultSiteKey) {
        Optional<RestItem> found = restItem(item);
        if (found.isPresent() && found.get().gone()) {
            return Outcome.goneItem();
        }
        List<Bundle> bundles = found.map(RestItem::bundles).orElse(List.of());
        DumStructuredRecord.Builder rec = DumStructuredRecord.builder(contentId, SCHEMA);
        Dim dim = new Dim(item.dim());
        rec.set(DumOaiPmhSource.TITLE, dim.values(DC, "title", null).stream().findFirst().orElse(null),
                dimProvenance("dc.title"));
        setText(rec, dim, bundles, contentId);
        List<String> authors = new ArrayList<>(dim.values(DC, "contributor", "author"));
        authors.addAll(dim.values(DC, "creator", null));
        rec.set(DumOaiPmhSource.AUTHORS, distinct(authors), dimProvenance("dc.contributor.author"));
        rec.set(ADVISORS, distinct(dim.matching(DC, "contributor", q -> q != null && q.startsWith("advisor"))),
                dimProvenance("dc.contributor.advisor"));
        rec.set(DumOaiPmhSource.SUBJECTS, distinct(dim.matching(DC, "subject", q -> true)),
                dimProvenance("dc.subject"));
        List<String> issued = dim.values(DC, "date", "issued");
        Optional<String> date = issued.stream().map(DumOaiPmhSource::isoDate).flatMap(Optional::stream).findFirst();
        if (date.isPresent()) {
            rec.set(DumOaiPmhSource.DATE, date.get(), dimProvenance("dc.date.issued"));
        } else {
            rec.set(DumOaiPmhSource.DATE_TEXT, issued.stream().findFirst().orElse(null),
                    dimProvenance("dc.date.issued"));
        }
        rec.set(DumOaiPmhSource.TYPE, distinct(dim.values(DC, "type", null)), dimProvenance("dc.type"));
        rec.set(DumOaiPmhSource.PUBLISHER, distinct(dim.values(DC, "publisher", null)),
                dimProvenance("dc.publisher"));
        List<String> languages = new ArrayList<>(dim.values(DC, "language", "iso"));
        languages.addAll(dim.values(DC, "language", null));
        rec.set(DumOaiPmhSource.LANGUAGE, distinct(languages), dimProvenance("dc.language.iso"));
        rec.set(DumOaiPmhSource.URL, handleUrl(dim.values(DC, "identifier", "uri"), restHost).orElse(null),
                dimProvenance("dc.identifier.uri"));
        rec.set(DumOaiPmhSource.RELATIONS, distinct(dim.matching(DC, "relation", q -> true)),
                dimProvenance("dc.relation"));
        rec.set(COMMUNITY, names(item.sets(), COMMUNITY_SET), restProvenance("setSpec com_ -> core/communities"));
        rec.set(COLLECTION, names(item.sets(), COLLECTION_SET), restProvenance("setSpec col_ -> core/collections"));
        rec.set(IMAGE, firstBitstream(bundles, "THUMBNAIL").map(this::contentUrl).orElse(null),
                restProvenance("THUMBNAIL"));
        rec.set(SITE_KEY, siteKey != null ? siteKey : defaultSiteKey,
                new DumFieldProvenance("config", "siteKey", null, null));
        return new Outcome(Optional.of(rec.build()), false);
    }

    /** {@code text}: the abstract, else the description; then the full text when that is on. */
    private void setText(DumStructuredRecord.Builder rec, Dim dim, List<Bundle> bundles, String contentId) {
        List<String> parts = new ArrayList<>(dim.values(DC, "description", "abstract"));
        String locator = parts.isEmpty() ? null : "dim:dc.description.abstract";
        if (parts.isEmpty()) {
            parts.addAll(dim.values(DC, "description", null));
            locator = parts.isEmpty() ? null : "dim:dc.description";
        }
        Optional<FullText> found = fullText ? fullText(bundles, contentId) : Optional.empty();
        if (found.isPresent()) {
            parts.add(found.get().text());
            locator = (locator == null ? "" : locator + " + ") + "fulltext:" + found.get().bundle() + ":"
                    + found.get().url();
        }
        rec.set(DumOaiPmhSource.TEXT, parts.isEmpty() ? null : String.join("\n\n", parts),
                locator == null ? null : DumFieldProvenance.oaiPmh(locator));
    }

    /** DSpace's own extracted text first, then the item's PDFs; the first that reads wins. */
    private Optional<FullText> fullText(List<Bundle> bundles, String contentId) {
        List<FullTextCandidate> candidates = new ArrayList<>();
        bundle(bundles, "TEXT").ifPresent(b -> b.bitstreams().forEach(
                bs -> candidates.add(new FullTextCandidate("TEXT", contentUrl(bs)))));
        bundle(bundles, "ORIGINAL").ifPresent(b -> b.bitstreams().stream()
                .filter(bs -> bs.name() != null && bs.name().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                .limit(MAX_PDF_PROBES)
                .forEach(bs -> candidates.add(new FullTextCandidate("ORIGINAL", contentUrl(bs)))));
        for (FullTextCandidate candidate : candidates) {
            try {
                Optional<String> text = rest.text(candidate.url(), restHost).filter(t -> !t.isBlank());
                if (text.isPresent()) {
                    return Optional.of(new FullText(candidate.bundle(), candidate.url(), text.get()));
                }
            } catch (IOException | RuntimeException e) {
                log.info("DSpace item '{}' gave no text from {}: {}", contentId, candidate.url(), e.getMessage());
            }
        }
        return Optional.empty();
    }

    private record FullTextCandidate(String bundle, String url) {
    }

    private record FullText(String bundle, String url, String text) {
    }

    /**
     * The item behind a record, looked up in REST by its handle; empty when the record names no
     * handle or REST could not say — the record is then indexed from its metadata alone.
     */
    private Optional<RestItem> restItem(DumOaiPmhResponse.Item item) {
        Optional<String> handle = handle(item);
        if (handle.isEmpty()) {
            log.debug("DSpace record '{}' names no handle; no REST lookup", item.identifier());
            return Optional.empty();
        }
        String url = restUrl + "/pid/find?id=" + URLEncoder.encode(handle.get(), StandardCharsets.UTF_8)
                .replace("%2F", "/");
        try {
            DumDSpaceRest.Response response = rest.get(url, restHost);
            if (response.denied()) {
                log.info("DSpace item {} is not public (HTTP {}); not indexed", handle.get(), response.status());
                return Optional.of(RestItem.GONE);
            }
            if (!response.ok()) {
                log.info("DSpace item {} was not found in REST (HTTP {}); indexed from its metadata alone",
                        handle.get(), response.status());
                return Optional.empty();
            }
            JSONObject json = new JSONObject(response.body());
            if (json.optBoolean("withdrawn", false) || !json.optBoolean("discoverable", true)) {
                log.info("DSpace item {} is withdrawn or not discoverable; not indexed", handle.get());
                return Optional.of(RestItem.GONE);
            }
            String uuid = json.optString("uuid", json.optString("id", ""));
            return Optional.of(new RestItem(false, uuid.isBlank() ? List.of() : bundles(uuid)));
        } catch (IOException | JSONException e) {
            log.info("DSpace item {} could not be read from REST: {}", handle.get(), e.getMessage());
            return Optional.empty();
        }
    }

    private record RestItem(boolean gone, List<Bundle> bundles) {
        static final RestItem GONE = new RestItem(true, List.of());
    }

    record Bundle(String name, List<Bitstream> bitstreams) {
    }

    record Bitstream(String uuid, String name) {
    }

    /** The item's bundles with their bitstreams, embedded when REST can, else one call per bundle. */
    private List<Bundle> bundles(String itemUuid) throws IOException {
        DumDSpaceRest.Response response = rest.get(
                restUrl + "/core/items/" + itemUuid + "/bundles?size=100&embed=bitstreams", restHost);
        if (!response.ok()) {
            log.info("DSpace item {} bundles answered HTTP {}", itemUuid, response.status());
            return List.of();
        }
        List<Bundle> bundles = new ArrayList<>();
        for (JSONObject bundle : embedded(new JSONObject(response.body()), "bundles")) {
            String name = bundle.optString("name", "");
            JSONObject inline = bundle.optJSONObject("_embedded");
            List<JSONObject> bitstreams;
            if (inline != null && inline.optJSONObject("bitstreams") != null) {
                bitstreams = embedded(inline.getJSONObject("bitstreams"), "bitstreams");
            } else if (needsBitstreams(name)) {
                bitstreams = bitstreamsOf(bundle.optString("uuid", ""));
            } else {
                bitstreams = List.of();
            }
            bundles.add(new Bundle(name, bitstreams.stream()
                    .map(b -> new Bitstream(b.optString("uuid", ""), b.optString("name", null)))
                    .filter(b -> !b.uuid().isBlank()).toList()));
        }
        return bundles;
    }

    private boolean needsBitstreams(String bundle) {
        return "THUMBNAIL".equals(bundle) || fullText && ("TEXT".equals(bundle) || "ORIGINAL".equals(bundle));
    }

    private List<JSONObject> bitstreamsOf(String bundleUuid) throws IOException {
        if (bundleUuid.isBlank()) {
            return List.of();
        }
        DumDSpaceRest.Response response = rest.get(restUrl + "/core/bundles/" + bundleUuid + "/bitstreams",
                restHost);
        return response.ok() ? embedded(new JSONObject(response.body()), "bitstreams") : List.of();
    }

    private static List<JSONObject> embedded(JSONObject page, String key) {
        JSONObject embedded = page.optJSONObject("_embedded");
        JSONArray array = embedded == null ? null : embedded.optJSONArray(key);
        List<JSONObject> objects = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) {
                    objects.add(object);
                }
            }
        }
        return objects;
    }

    private static Optional<Bundle> bundle(List<Bundle> bundles, String name) {
        return bundles.stream().filter(b -> name.equals(b.name())).findFirst();
    }

    private static Optional<Bitstream> firstBitstream(List<Bundle> bundles, String name) {
        return bundle(bundles, name).flatMap(b -> b.bitstreams().stream().findFirst());
    }

    private String contentUrl(Bitstream bitstream) {
        return restUrl + "/core/bitstreams/" + bitstream.uuid() + "/content";
    }

    /** The names of the record's sets of one kind; a set whose name REST did not give is left out. */
    private List<String> names(List<String> sets, String prefix) {
        Map<String, String> known = setNames();
        return sets.stream().filter(s -> s.startsWith(prefix)).map(known::get).filter(Objects::nonNull)
                .distinct().toList();
    }

    /** Every community and collection name by its set spec, read once per run. */
    private synchronized Map<String, String> setNames() {
        if (setNames == null) {
            Map<String, String> names = new LinkedHashMap<>();
            readNames("communities", COMMUNITY_SET, names);
            readNames("collections", COLLECTION_SET, names);
            setNames = names;
        }
        return setNames;
    }

    private void readNames(String kind, String prefix, Map<String, String> names) {
        for (int page = 0; page < MAX_SET_PAGES; page++) {
            String url = restUrl + "/core/" + kind + "?page=" + page + "&size=" + SET_PAGE_SIZE;
            try {
                DumDSpaceRest.Response response = rest.get(url, restHost);
                if (!response.ok()) {
                    notes.add("DSpace %s names unavailable: HTTP %d from %s".formatted(kind, response.status(), url));
                    return;
                }
                JSONObject json = new JSONObject(response.body());
                for (JSONObject dso : embedded(json, kind)) {
                    String handle = dso.optString("handle", "");
                    String name = dso.optString("name", "");
                    if (!handle.isBlank() && !name.isBlank()) {
                        names.put(setSpec(prefix, handle), name.strip());
                    }
                }
                JSONObject paging = json.optJSONObject("page");
                if (paging == null || page + 1 >= paging.optInt("totalPages", 0)) {
                    return;
                }
            } catch (IOException | JSONException e) {
                notes.add("DSpace %s names unavailable: %s".formatted(kind, e.getMessage()));
                return;
            }
        }
        notes.add("DSpace %s names stopped after %d pages".formatted(kind, MAX_SET_PAGES));
    }

    /** The set spec DSpace's OAI-PMH gives a handle: {@code 123456789/7} is {@code col_123456789_7}. */
    static String setSpec(String prefix, String handle) {
        return prefix + handle.strip().replace('/', '_');
    }

    /**
     * The record's handle: from a handle URL in {@code dc.identifier.uri}, else from the OAI
     * identifier {@code oai:host:prefix/suffix}.
     */
    static Optional<String> handle(DumOaiPmhResponse.Item item) {
        for (DumOaiPmhResponse.DimField field : item.dim()) {
            if (DC.equals(field.schema()) && "identifier".equals(field.element())
                    && "uri".equals(field.qualifier())) {
                Optional<String> fromUrl = handleOfUrl(field.value());
                if (fromUrl.isPresent()) {
                    return fromUrl;
                }
            }
        }
        String identifier = item.identifier();
        if (identifier != null && identifier.startsWith("oai:")) {
            String tail = identifier.substring(identifier.lastIndexOf(':') + 1);
            if (tail.contains("/")) {
                return Optional.of(tail);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> handleOfUrl(String value) {
        String text = value.strip();
        String lower = text.toLowerCase(Locale.ROOT);
        int at = lower.indexOf("/handle/");
        int length = "/handle/".length();
        if (at < 0) {
            at = lower.indexOf("hdl.handle.net/");
            length = "hdl.handle.net/".length();
        }
        if (at < 0) {
            return Optional.empty();
        }
        String handle = text.substring(at + length);
        int end = indexOfAny(handle, '?', '#');
        handle = end < 0 ? handle : handle.substring(0, end);
        handle = handle.endsWith("/") ? handle.substring(0, handle.length() - 1) : handle;
        return handle.contains("/") ? Optional.of(handle) : Optional.empty();
    }

    private static int indexOfAny(String text, char a, char b) {
        int first = text.indexOf(a);
        int second = text.indexOf(b);
        return first < 0 ? second : second < 0 ? first : Math.min(first, second);
    }

    /**
     * The first {@code dc.identifier.uri} that is a handle link a visitor can open, else the generic
     * URL rule's choice; an address on a private or dotless host is never one (D321).
     */
    static Optional<String> handleUrl(List<String> uris, String host) {
        return uris.stream().map(String::strip)
                .filter(u -> u.regionMatches(true, 0, "http", 0, 4) && handleOfUrl(u).isPresent()
                        && DumOaiPmhSource.isPublic(u))
                .findFirst().or(() -> DumOaiPmhSource.url(uris, host));
    }

    private static List<String> distinct(List<String> values) {
        Set<String> seen = new LinkedHashSet<>(values);
        return List.copyOf(seen);
    }

    private static DumFieldProvenance dimProvenance(String field) {
        return DumFieldProvenance.oaiPmh("dim:" + field);
    }

    private static DumFieldProvenance restProvenance(String locator) {
        return new DumFieldProvenance("dspace-rest", locator, null, null);
    }

    /** A record's {@code dim} values, looked up by schema, element and qualifier. */
    private record Dim(List<DumOaiPmhResponse.DimField> fields) {

        /** The values with exactly this qualifier; {@code null} means unqualified. */
        List<String> values(String schema, String element, String qualifier) {
            return matching(schema, element, q -> Objects.equals(q, qualifier));
        }

        List<String> matching(String schema, String element, Predicate<String> qualifier) {
            return fields.stream().filter(f -> schema.equals(f.schema()) && element.equals(f.element())
                    && qualifier.test(f.qualifier())).map(DumOaiPmhResponse.DimField::value).toList();
        }
    }

    public static final class Builder {
        private final String restUrl;
        private final DumDSpaceRest rest;
        private boolean fullText = true;
        private String siteKey;

        private Builder(String restUrl, DumDSpaceRest rest) {
            String url = restUrl == null ? "" : restUrl.strip();
            if (!(url.regionMatches(true, 0, "http://", 0, 7) || url.regionMatches(true, 0, "https://", 0, 8))
                    || URI.create(url).getHost() == null) {
                throw new IllegalArgumentException("The DSpace profile needs an http(s) REST API URL");
            }
            if (rest == null) {
                throw new IllegalArgumentException("A DSpace REST client is required");
            }
            this.restUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            this.rest = rest;
        }

        /**
         * Whether each item's text also carries its {@code TEXT} bundle, else its PDF. On by
         * default; bounded by the client's size caps and pacing.
         */
        public Builder fullText(boolean fullText) {
            this.fullText = fullText;
            return this;
        }

        /** The {@code site_key} every record carries; blank means the OAI-PMH base URL's host. */
        public Builder siteKey(String siteKey) {
            this.siteKey = siteKey == null || siteKey.isBlank() ? null : siteKey.strip();
            return this;
        }

        public DumDSpaceProfile build() {
            return new DumDSpaceProfile(this);
        }
    }
}
