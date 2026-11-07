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
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.viglet.dumont.connector.commons.spi.structured.DumEmbeddingRole;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;
import com.viglet.turing.commons.se.field.TurSEFieldType;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link DumStructuredSource} that harvests an OAI-PMH 2.0 repository (roadmap
 * <strong>D315</strong>) — the protocol DSpace, EPrints, OJS, Tainacan and Koha all speak, so one
 * source covers every institutional repository and journal that runs them.
 *
 * <p>
 * <strong>The harvest.</strong> {@link #discover()} issues {@code ListRecords} once, with the
 * metadata prefix (default {@code oai_dc}), the optional set and, on an incremental run, a
 * {@code from}; then follows each {@code resumptionToken} until the repository sends none. Every
 * record is kept in memory by its header identifier, so {@link #extract(String)} never refetches.
 * A {@code noRecordsMatch} error is an empty, complete harvest — the repository's way of saying
 * nothing changed. Any other OAI error, an HTTP failure, a body that is not OAI-PMH, or a token the
 * repository repeats ends the harvest <em>incomplete</em>: {@link #discoveryComplete()} answers
 * {@code false} and the plugin finishes the run standalone, so nothing it never reached is
 * deindexed.
 *
 * <p>
 * <strong>Increments.</strong> A harvest that completed stores its first response's
 * {@code responseDate} in the source's {@link DumOaiPmhState}; one that did not leaves the old
 * watermark, so the next run asks again for everything the failed one missed. The next run sends
 * the watermark's <em>day</em> as {@code from}: day granularity is the one every repository must
 * accept, and re-harvesting a few hours twice costs less than a {@code badArgument} from a
 * repository that only speaks days. A run that sent {@code from} is {@link #incremental()}, so it
 * never sweeps what it was not sent; a first or a full run is not, and sweeps as usual.
 *
 * <p>
 * <strong>Deletions.</strong> A header with {@code status="deleted"} is never discovered; its
 * identifier is reported through {@link #goneIds()} (roadmap D114), which the plugin deindexes at
 * once whatever the run mode. A repository that keeps no deletion record ({@code deletedRecord}
 * {@code no}) never sends one, and its removals leave on the next full run's sweep.
 *
 * <p>
 * <strong>Mapping.</strong> Dublin Core maps onto a fixed {@link #SCHEMA}. Nothing is invented: a
 * date that is not a year, a year-month, a day or an instant goes to {@code dateText} as written
 * rather than into the {@code DATE} field, and {@code url} is a {@code dc:identifier} that is
 * already an http(s) URL — a handle or DOI link when the record has one, else the first — never
 * one built from the OAI identifier.
 *
 * <p>
 * <strong>Full text.</strong> Off unless a {@link DumOaiPmhFullTextFetcher} is given. With one,
 * {@link #extract(String)} looks at the record's {@code dc:identifier} and {@code dc:relation}
 * values that are http(s) URLs on the base URL's own host — never another host — trying those that
 * end in {@code .pdf} first and at most {@value #MAX_FULL_TEXT_PROBES} in all, and appends the first
 * PDF text it gets to {@code text}, after the description. A failed or empty fetch leaves the
 * record as the metadata alone describes it.
 *
 * <p>
 * <strong>DSpace.</strong> With a {@link DumDSpaceProfile} (roadmap <strong>D314</strong>) the
 * harvest asks for {@code dim}, the schema is {@link DumDSpaceProfile#SCHEMA}, and each record is
 * mapped and completed from the repository's REST API by the profile; an item REST reports
 * withdrawn, private or not discoverable joins {@link #goneIds()} instead of being indexed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumOaiPmhSource implements DumStructuredSource {

    /** The metadata format every OAI-PMH repository must serve. */
    public static final String DEFAULT_METADATA_PREFIX = "oai_dc";

    // Turing's own names where a WordPress post has the same concept (D326): one SN site holding
    // both shares one author facet, one date for cards, sorts and periods, and one snippet.
    public static final String TITLE = "title";
    public static final String TEXT = "text";
    public static final String ABSTRACT = "abstract";
    public static final String AUTHORS = "author";
    public static final String SUBJECTS = "subjects";
    public static final String DATE = "publication_date";
    public static final String DATE_TEXT = "dateText";
    public static final String TYPE = "type";
    public static final String PUBLISHER = "publisher";
    public static final String LANGUAGE = "language";
    public static final String URL = "url";
    public static final String RELATIONS = "relations";

    /** The Dublin Core schema every OAI-PMH source publishes. */
    public static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .field(DumStructuredField.builder(TITLE).type(TurSEFieldType.TEXT).mandatory(true)
                    .embeddingRole(DumEmbeddingRole.TITLE)
                    .description("dc:title").build())
            .field(DumStructuredField.builder(TEXT).type(TurSEFieldType.TEXT)
                    .embeddingRole(DumEmbeddingRole.BODY)
                    .description("dc:description, every value; then a same-host PDF's text when full text is on")
                    .build())
            .field(DumStructuredField.builder(ABSTRACT).type(TurSEFieldType.TEXT)
                    .description("The first dc:description").build())
            .field(DumStructuredField.builder(AUTHORS).multiValued(true).facet(true)
                    .description("dc:creator").build())
            .field(DumStructuredField.builder(SUBJECTS).multiValued(true).facet(true)
                    .description("dc:subject").build())
            .field(DumStructuredField.builder(DATE).type(TurSEFieldType.DATE)
                    .description("The first dc:date that reads as a date").build())
            .field(DumStructuredField.builder(DATE_TEXT)
                    .description("dc:date as written, when no value reads as a date").build())
            .field(DumStructuredField.builder(TYPE).multiValued(true).facet(true)
                    .description("dc:type").build())
            .field(DumStructuredField.builder(PUBLISHER).multiValued(true).facet(true)
                    .description("dc:publisher").build())
            .field(DumStructuredField.builder(LANGUAGE).multiValued(true).facet(true)
                    .description("dc:language").build())
            .field(DumStructuredField.builder(URL)
                    .description("The dc:identifier that is an http(s) URL, handle or DOI first").build())
            .field(DumStructuredField.builder(RELATIONS).multiValued(true)
                    .description("dc:relation").build())
            .build();

    /** The most same-host URLs one record's full text is looked for at. */
    static final int MAX_FULL_TEXT_PROBES = 3;

    private static final Pattern YEAR = Pattern.compile("\\d{4}");
    private static final Pattern YEAR_MONTH = Pattern.compile("\\d{4}-\\d{2}");
    private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final DateTimeFormatter ISO_INSTANT_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final String baseUrl;
    private final String metadataPrefix;
    private final String set;
    private final String sourceName;
    private final String providerName;
    private final List<String> siteNames;
    private final Locale locale;
    private final DumOaiPmhFetcher fetcher;
    private final DumOaiPmhState state;
    private final boolean incrementalEnabled;
    private final DumOaiPmhFullTextFetcher fullText;
    private final DumDSpaceProfile dspace;
    private final String host;

    private final List<String> notes = new ArrayList<>();
    private final Set<String> gone = new LinkedHashSet<>();
    private Map<String, DumOaiPmhResponse.Item> recordsById;
    private String from;
    private boolean everything;
    private boolean complete;
    private int calls;

    private DumOaiPmhSource(Builder builder) {
        this.baseUrl = builder.baseUrl.trim();
        this.metadataPrefix = builder.dspace != null ? DumDSpaceProfile.METADATA_PREFIX : builder.metadataPrefix;
        this.set = builder.set;
        this.sourceName = builder.sourceName;
        this.providerName = builder.providerName;
        this.siteNames = List.copyOf(builder.siteNames);
        this.locale = builder.locale;
        this.fetcher = builder.fetcher;
        this.state = builder.state;
        this.incrementalEnabled = builder.incremental;
        this.fullText = builder.fullText;
        this.dspace = builder.dspace;
        this.host = URI.create(this.baseUrl).getHost();
    }

    public static Builder builder(String baseUrl, DumOaiPmhFetcher fetcher) {
        return new Builder(baseUrl, fetcher);
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
        return dspace != null ? DumDSpaceProfile.SCHEMA : SCHEMA;
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

    /** Whether this run asked only for what changed since the watermark. */
    @Override
    public synchronized boolean incremental() {
        records();
        return from != null;
    }

    /** D341: this run harvests from the start, whatever the watermark says. */
    @Override
    public synchronized void listEverything() {
        everything = true;
    }

    /** The identifiers the repository reported deleted during this harvest. */
    @Override
    public synchronized List<String> goneIds() {
        records();
        return List.copyOf(gone);
    }

    /** Why the harvest stopped early, if it did: the failure or the OAI error that ended it. */
    @Override
    public synchronized List<String> runNotes() {
        records();
        List<String> all = new ArrayList<>(notes);
        if (dspace != null) {
            all.addAll(dspace.notes());
        }
        return List.copyOf(all);
    }

    /** How many requests the harvest made. */
    public synchronized int calls() {
        records();
        return calls;
    }

    @Override
    public Optional<DumStructuredRecord> extract(String contentId) {
        DumOaiPmhResponse.Item item = records().get(contentId);
        if (item == null) {
            return Optional.empty();
        }
        if (dspace != null) {
            DumDSpaceProfile.Outcome outcome = dspace.extract(contentId, item, host);
            if (outcome.gone()) {
                synchronized (this) {
                    gone.add(contentId);
                }
            }
            return outcome.record();
        }
        DumStructuredRecord.Builder rec = DumStructuredRecord.builder(contentId, SCHEMA);
        rec.set(TITLE, item.values("title").stream().findFirst().orElse(null), provenance("title"));
        setText(rec, item);
        rec.set(AUTHORS, item.values("creator"), provenance("creator"));
        rec.set(SUBJECTS, item.values("subject"), provenance("subject"));
        List<String> dates = item.values("date");
        Optional<String> date = dates.stream().map(DumOaiPmhSource::isoDate).flatMap(Optional::stream)
                .findFirst();
        if (date.isPresent()) {
            rec.set(DATE, date.get(), provenance("date"));
        } else {
            rec.set(DATE_TEXT, dates.stream().findFirst().orElse(null), provenance("date"));
        }
        rec.set(TYPE, item.values("type"), provenance("type"));
        rec.set(PUBLISHER, item.values("publisher"), provenance("publisher"));
        rec.set(LANGUAGE, item.values("language"), provenance("language"));
        rec.set(URL, url(item.values("identifier"), host).orElse(null), provenance("identifier"));
        rec.set(RELATIONS, item.values("relation"), provenance("relation"));
        return Optional.of(rec.build());
    }

    /** {@code text}: every description, then the full text of a same-host PDF when that is on. */
    private void setText(DumStructuredRecord.Builder rec, DumOaiPmhResponse.Item item) {
        List<String> parts = new ArrayList<>(item.values("description"));
        String locator = parts.isEmpty() ? null : "dc:description";
        // The card's snippet (D326): the first description, under Turing's own field.
        rec.set(ABSTRACT, parts.stream().findFirst().orElse(null), provenance("description"));
        Optional<FullText> found = fullText(item);
        if (found.isPresent()) {
            parts.add(found.get().text());
            locator = (locator == null ? "" : locator + " + ") + "fulltext:" + found.get().url();
        }
        rec.set(TEXT, parts.isEmpty() ? null : String.join("\n\n", parts),
                locator == null ? provenance("description") : DumFieldProvenance.oaiPmh(locator));
    }

    private Optional<FullText> fullText(DumOaiPmhResponse.Item item) {
        if (fullText == null) {
            return Optional.empty();
        }
        for (String url : fullTextCandidates(item, host)) {
            try {
                Optional<String> text = fullText.text(url, host).filter(t -> !t.isBlank());
                if (text.isPresent()) {
                    return Optional.of(new FullText(url, text.get()));
                }
            } catch (IOException | RuntimeException e) {
                log.info("OAI-PMH source '{}' read no full text for '{}' from {}: {}", sourceName,
                        item.identifier(), url, e.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * The record's {@code dc:identifier} and {@code dc:relation} values a full text may be read
     * from: http(s) URLs on {@code host}, those ending in {@code .pdf} first, at most
     * {@link #MAX_FULL_TEXT_PROBES}.
     */
    static List<String> fullTextCandidates(DumOaiPmhResponse.Item item, String host) {
        List<String> values = new ArrayList<>(item.values("identifier"));
        values.addAll(item.values("relation"));
        List<URI> onHost = values.stream().map(String::strip).distinct()
                .map(DumOaiPmhSource::uri).flatMap(Optional::stream)
                .filter(uri -> isOnHost(uri, host)).toList();
        List<String> ordered = new ArrayList<>();
        onHost.stream().filter(DumOaiPmhSource::endsWithPdf).map(URI::toString).forEach(ordered::add);
        onHost.stream().filter(uri -> !endsWithPdf(uri)).map(URI::toString).forEach(ordered::add);
        return ordered.size() > MAX_FULL_TEXT_PROBES ? ordered.subList(0, MAX_FULL_TEXT_PROBES) : ordered;
    }

    private static Optional<URI> uri(String value) {
        try {
            return Optional.of(new URI(value));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private static boolean isOnHost(URI uri, String host) {
        return DumHttpOaiPmhFullTextFetcher.onHost(uri, host);
    }

    private static boolean endsWithPdf(URI uri) {
        return uri.getPath() != null && uri.getPath().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private record FullText(String url, String text) {
    }

    private static DumFieldProvenance provenance(String element) {
        return DumFieldProvenance.oaiPmh("dc:" + element);
    }

    /**
     * The record's link: a {@code dc:identifier} that is an http(s) URL a visitor can open,
     * preferring a handle ({@code hdl.handle.net} or a {@code /handle/} path) or a DOI
     * ({@code doi.org}) link, which outlive a repository's own URL scheme, then one on the base
     * URL's own {@code host}, then the first. An address on a private, loopback or link-local IP,
     * or on a bare hostname with no dot, is never chosen (D321): a repository can leak its internal
     * host into its Dublin Core. Empty when no identifier qualifies.
     */
    static Optional<String> url(List<String> identifiers, String host) {
        List<String> urls = identifiers.stream().map(String::strip)
                .filter(v -> v.regionMatches(true, 0, "http://", 0, 7)
                        || v.regionMatches(true, 0, "https://", 0, 8))
                .filter(DumOaiPmhSource::isPublic)
                .toList();
        return urls.stream().filter(DumOaiPmhSource::isPersistent).findFirst()
                .or(() -> urls.stream().filter(u -> uri(u).filter(x -> isOnHost(x, host)).isPresent()).findFirst())
                .or(() -> urls.stream().findFirst());
    }

    /** Whether a URL's host is one a visitor can reach: no private IP literal, no dotless name. */
    static boolean isPublic(String url) {
        String host = uri(url).map(URI::getHost).orElse(null);
        if (host == null || host.isBlank()) {
            return false;
        }
        String bare = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        if (bare.contains(":") || IPV4.matcher(bare).matches()) {
            return !isPrivateAddress(bare);
        }
        return bare.contains(".") && !bare.toLowerCase(Locale.ROOT).endsWith(".localhost");
    }

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    /** An IP literal (never a name: nothing is resolved) that is private, loopback, link-local or unset. */
    private static boolean isPrivateAddress(String literal) {
        try {
            // A literal already matched as one: getByName parses it and never resolves.
            InetAddress address = InetAddress.getByName(literal);
            byte[] bytes = address.getAddress();
            boolean uniqueLocal = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
            boolean sharedCgnat = bytes.length == 4 && (bytes[0] & 0xff) == 100 && (bytes[1] & 0xc0) == 64;
            return address.isSiteLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress() || uniqueLocal || sharedCgnat;
        } catch (UnknownHostException | IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean isPersistent(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("hdl.handle.net/") || lower.contains("/handle/")
                || lower.contains("doi.org/");
    }

    /**
     * {@code value} as the ISO instant a {@code DATE} field takes, when it is a year, a
     * year-month, a day or an instant; a partial date reads as its first day. Empty otherwise.
     */
    static Optional<String> isoDate(String value) {
        String text = value.strip();
        try {
            LocalDate day = null;
            if (YEAR.matcher(text).matches()) {
                day = LocalDate.of(Integer.parseInt(text), 1, 1);
            } else if (YEAR_MONTH.matcher(text).matches()) {
                day = YearMonth.parse(text).atDay(1);
            } else if (DAY.matcher(text).matches()) {
                day = LocalDate.parse(text);
            }
            if (day != null) {
                return Optional.of(ISO_INSTANT_SECONDS.format(day.atStartOfDay(ZoneOffset.UTC)));
            }
            return Optional.of(ISO_INSTANT_SECONDS.format(OffsetDateTime.parse(text)));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private synchronized Map<String, DumOaiPmhResponse.Item> records() {
        if (recordsById == null) {
            recordsById = harvest();
        }
        return recordsById;
    }

    private Map<String, DumOaiPmhResponse.Item> harvest() {
        Map<String, DumOaiPmhResponse.Item> found = new LinkedHashMap<>();
        from = harvestFrom();
        Set<String> tokens = new HashSet<>();
        String firstResponseDate = null;
        String url = firstUrl();
        while (true) {
            DumOaiPmhResponse page = call(url);
            if (page == null) {
                return stopped(found, null);
            }
            if (firstResponseDate == null) {
                firstResponseDate = page.responseDate();
            }
            if (!page.errors().isEmpty()) {
                if (page.errors().keySet().equals(Set.of("noRecordsMatch"))) {
                    break;
                }
                return stopped(found, "stopped: the repository answered %s".formatted(describe(page.errors())));
            }
            page.records().forEach(item -> keep(found, item));
            String token = page.resumptionToken();
            if (token == null) {
                break;
            }
            if (!tokens.add(token)) {
                return stopped(found, "stopped: the repository repeated a resumption token");
            }
            url = withQuery("verb=ListRecords&resumptionToken=" + encode(token));
        }
        complete = true;
        if (firstResponseDate != null) {
            state.watermark(firstResponseDate);
        }
        return found;
    }

    /**
     * The day this harvest asks from: the watermark's, when the source is incremental and this run
     * was not asked to list everything (D341); else none, which harvests from the start.
     */
    private String harvestFrom() {
        if (!incrementalEnabled || everything) {
            return null;
        }
        return state.watermark().map(DumOaiPmhSource::day).orElse(null);
    }

    /** A later record for the same identifier wins, deleted or not. */
    private void keep(Map<String, DumOaiPmhResponse.Item> found, DumOaiPmhResponse.Item item) {
        if (item.identifier() == null) {
            log.warn("OAI-PMH source '{}' skipped a record with no header identifier", sourceName);
        } else if (item.deleted()) {
            found.remove(item.identifier());
            gone.add(item.identifier());
        } else {
            gone.remove(item.identifier());
            found.put(item.identifier(), item);
        }
    }

    private DumOaiPmhResponse call(String url) {
        calls++;
        String body;
        try {
            body = fetcher.fetch(url);
        } catch (IOException e) {
            log.warn("OAI-PMH source '{}' call {} failed: {}", sourceName, calls, failure(e));
            notes.add("stopped: call %d failed (%s)".formatted(calls, failure(e)));
            return null;
        }
        try {
            return DumOaiPmhResponse.parse(body);
        } catch (IOException e) {
            log.warn("OAI-PMH source '{}' call {} returned no OAI-PMH response: {}", sourceName, calls,
                    e.getMessage());
            notes.add("stopped: call %d returned no OAI-PMH response".formatted(calls));
            return null;
        }
    }

    /**
     * What a failed call says in the run notes. The JDK client throws a {@code ConnectException}
     * with no message when nothing listens on the port, which read "call 1 failed (null)"; the
     * exception's simple name says what happened.
     */
    static String failure(IOException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private Map<String, DumOaiPmhResponse.Item> stopped(Map<String, DumOaiPmhResponse.Item> found,
            String note) {
        if (note != null) {
            log.info("OAI-PMH source '{}' {}", sourceName, note);
            notes.add(note);
        }
        complete = false;
        return found;
    }

    private String firstUrl() {
        StringBuilder query = new StringBuilder("verb=ListRecords&metadataPrefix=")
                .append(encode(metadataPrefix));
        if (set != null) {
            query.append("&set=").append(encode(set));
        }
        if (from != null) {
            query.append("&from=").append(encode(from));
        }
        return withQuery(query.toString());
    }

    private String withQuery(String query) {
        if (baseUrl.endsWith("?") || baseUrl.endsWith("&")) {
            return baseUrl + query;
        }
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + query;
    }

    private static String describe(Map<String, String> errors) {
        List<String> parts = new ArrayList<>();
        errors.forEach((code, message) -> parts.add(message == null ? code : code + ": " + message));
        return String.join("; ", parts);
    }

    /** The day part of a {@code responseDate}, the {@code from} granularity every repository takes. */
    private static String day(String responseDate) {
        String text = responseDate.strip();
        return text.length() > 10 ? text.substring(0, 10) : text;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static final class Builder {
        private final String baseUrl;
        private final DumOaiPmhFetcher fetcher;
        private String metadataPrefix = DEFAULT_METADATA_PREFIX;
        private String set;
        private String sourceName;
        private String providerName = "OAI-PMH";
        private Collection<String> siteNames = List.of();
        private Locale locale = Locale.US;
        private DumOaiPmhState state = new DumInMemoryOaiPmhState();
        private boolean incremental = true;
        private DumOaiPmhFullTextFetcher fullText;
        private DumDSpaceProfile dspace;

        private Builder(String baseUrl, DumOaiPmhFetcher fetcher) {
            if (baseUrl == null || !(baseUrl.strip().regionMatches(true, 0, "http://", 0, 7)
                    || baseUrl.strip().regionMatches(true, 0, "https://", 0, 8))) {
                throw new IllegalArgumentException("An http(s) OAI-PMH base URL is required");
            }
            if (fetcher == null) {
                throw new IllegalArgumentException("A fetcher is required");
            }
            this.baseUrl = baseUrl;
            this.fetcher = fetcher;
        }

        /** The metadata format to harvest; blank means {@value DumOaiPmhSource#DEFAULT_METADATA_PREFIX}. */
        public Builder metadataPrefix(String metadataPrefix) {
            this.metadataPrefix = metadataPrefix == null || metadataPrefix.isBlank()
                    ? DEFAULT_METADATA_PREFIX : metadataPrefix.strip();
            return this;
        }

        /** The OAI set to harvest; blank means the whole repository. */
        public Builder set(String set) {
            this.set = set == null || set.isBlank() ? null : set.strip();
            return this;
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

        /** Where the watermark lives between runs. Defaults to memory, which a restart forgets. */
        public Builder state(DumOaiPmhState state) {
            this.state = state == null ? new DumInMemoryOaiPmhState() : state;
            return this;
        }

        /**
         * Whether a run with a watermark asks only for what changed since it. Defaults to
         * {@code true}; {@code false} harvests in full every run, so the sweep sees everything.
         */
        public Builder incremental(boolean incremental) {
            this.incremental = incremental;
            return this;
        }

        /**
         * Reads each record's full text through {@code fullText}, from PDFs on the base URL's own
         * host. {@code null}, the default, leaves full text off.
         */
        public Builder fullText(DumOaiPmhFullTextFetcher fullText) {
            this.fullText = fullText;
            return this;
        }

        /**
         * Reads the repository as DSpace (roadmap D314): {@code dim} metadata whatever
         * {@link #metadataPrefix(String)} says, completed through {@code dspace}'s REST API.
         * {@code null}, the default, keeps the generic Dublin Core mapping.
         */
        public Builder dspace(DumDSpaceProfile dspace) {
            this.dspace = dspace;
            return this;
        }

        /** @throws IllegalArgumentException when the source has no name */
        public DumOaiPmhSource build() {
            if (sourceName == null || sourceName.isBlank()) {
                throw new IllegalArgumentException("A source name is required");
            }
            return new DumOaiPmhSource(this);
        }
    }
}
