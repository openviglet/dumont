/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.viglet.dumont.connector.api;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.viglet.dumont.commons.logging.DumIndexingLogWriters;
import com.viglet.dumont.commons.logging.IsoDateSerializer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Mirrors {@code TurLoggingAPI}: exposes the persistent indexing log written
 * by {@code DumLoggingIndexingLog} via either MongoDB or Redis (selected by
 * {@code dumont.logging.engine}). Returns an empty page when the engine is
 * {@code none} or the backing store is disabled.
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/connector/logging")
@Tag(name = "Logging API", description = "Connector indexing log history")
public class DumLoggingAPI {

    public static final String DATE = "date";
    public static final String CONTENT = "content";
    /** The two fields D69 and D71 made reliable, and D72 made queryable. */
    public static final String SOURCE_FIELD = "source";
    public static final String TRANSACTION_ID_FIELD = "transactionId";
    public static final String CONTENT_ID_FIELD = "contentId";
    public static final String TOTAL_ELEMENTS = "totalElements";
    public static final String TOTAL_PAGES = "totalPages";
    /**
     * How many rows the engine could not read (D74). Always present, so a caller never has to
     * tell "no unreadable rows" apart from "this response does not say".
     */
    public static final String UNREADABLE = "unreadable";
    public static final String PAGE = "page";
    public static final String PAGE_SIZE = "pageSize";
    public static final String ENGINE_NONE = "none";
    public static final String ENGINE_MONGODB = "mongodb";
    public static final String ENGINE_REDIS = "redis";

    private final String loggingEngine;

    private final boolean mongoEnabled;
    private final String mongoUri;
    private final String databaseName;
    private final String indexingCollectionName;

    private final boolean redisEnabled;
    private final String redisUri;
    private final String redisIndexingKey;

    /**
     * How long the Mongo driver may hunt for a server before the endpoint gives up, where the URI
     * does not say (roadmap <strong>D76</strong>). The driver's own default is 30s, which is a
     * sensible wait for a batch job and far too long for {@code status} — a screen polls it, and a
     * store that is down should say so rather than hold the request open for half a minute.
     */
    private static final long PROBE_SERVER_SELECTION_MS = 5_000L;

    /**
     * One client per store, not one per request (roadmap <strong>D76</strong>).
     *
     * <p>
     * A {@link MongoClient} owns a connection pool and a topology-monitoring thread; the driver's
     * guidance is one per application. This endpoint used to build one at each of four call sites
     * and close it again, paying the setup on every call and discarding the pool before it was
     * warm. {@code status} — the one a console screen polls — was the worst of them.
     *
     * <p>
     * Redis gets a pool rather than a shared {@link Jedis}, because a {@code Jedis} is a single
     * connection and is not thread-safe: sharing one across concurrent requests would trade this
     * cost for a correctness bug. Both are {@code null} when their engine is not the active one,
     * so a connector that logs to neither builds neither.
     */
    private final MongoClient mongoClient;
    private final JedisPool redisPool;

    public DumLoggingAPI(
            @Value("${dumont.logging.engine:none}") String loggingEngine,
            @Value("${dumont.mongodb.enabled:false}") boolean mongoEnabled,
            @Value("${dumont.mongodb.uri:mongodb://localhost:27017}") String mongoUri,
            @Value("${dumont.logging.database:dumontLog}") String databaseName,
            @Value("${dumont.logging.collection.indexing:indexing}") String indexingCollectionName,
            @Value("${dumont.redis.enabled:false}") boolean redisEnabled,
            @Value("${dumont.redis.uri:redis://localhost:6379/0}") String redisUri,
            @Value("${dumont.redis.key.indexing:dumontLog:indexing}") String redisIndexingKey) {
        this.loggingEngine = loggingEngine;
        this.mongoEnabled = mongoEnabled;
        this.mongoUri = mongoUri;
        this.databaseName = databaseName;
        this.indexingCollectionName = indexingCollectionName;
        this.redisEnabled = redisEnabled;
        this.redisUri = redisUri;
        this.redisIndexingKey = redisIndexingKey;
        this.mongoClient = isMongoActive() ? buildMongoClient() : null;
        this.redisPool = isRedisActive() ? buildRedisPool() : null;
    }

    /**
     * Neither builder connects — both the driver and the pool dial on first use — so a store that
     * is down does not stop the connector from starting. A malformed URI is the one case that
     * fails here, and it fails to {@code null}: a log the operator cannot reach is a 503 on the
     * log's own endpoints, not a connector that refuses to boot over a logging property.
     */
    private MongoClient buildMongoClient() {
        try {
            ConnectionString connectionString = new ConnectionString(mongoUri);
            MongoClientSettings.Builder settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString);
            // An explicit serverSelectionTimeoutMS in the URI is the operator's call and is kept.
            if (connectionString.getServerSelectionTimeout() == null) {
                settings.applyToClusterSettings(cluster -> cluster
                        .serverSelectionTimeout(PROBE_SERVER_SELECTION_MS, MILLISECONDS));
            }
            return MongoClients.create(settings.build());
        } catch (Exception e) {
            log.error("Indexing log: the MongoDB URI could not be read, so the log cannot be "
                    + "queried. Check dumont.mongodb.uri.", e);
            return null;
        }
    }

    private JedisPool buildRedisPool() {
        try {
            return new JedisPool(java.net.URI.create(redisUri));
        } catch (Exception e) {
            log.error("Indexing log: the Redis URI could not be read, so the log cannot be "
                    + "queried. Check dumont.redis.uri.", e);
            return null;
        }
    }

    /** Hands both stores back at shutdown; the pool's idle connections are not the JVM's to leak. */
    @PreDestroy
    void closeClients() {
        if (mongoClient != null) {
            mongoClient.close();
        }
        if (redisPool != null) {
            redisPool.close();
        }
    }

    /**
     * Whether the indexing log is on, and whether it actually answers (roadmap
     * <strong>D75</strong>).
     *
     * <p>
     * {@code configured} is what the properties say; {@code enabled} is what a ping says. They
     * used to be one field read from configuration alone, so a store that was named and
     * unreachable reported itself as on — and the reader that gates a screen on it showed an empty
     * history rather than a broken one.
     */
    @Operation(summary = "Indexing log feature flag, reachability and whether anything writes it")
    @GetMapping("status")
    public Map<String, Object> status() {
        boolean configured = isMongoActive() || isRedisActive();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("engine", loggingEngine);
        body.put("configured", configured);
        body.put("enabled", configured && reachable());
        /*
         * D84 — the third question, and the one the first two could not answer.
         *
         * `configured` and `enabled` both describe this endpoint's own client. The rows are
         * written by a Logback appender selected separately by `logging.config`, whose default
         * declares no store appender and switches the indexing logger off. So a deployment that
         * set the engine and nothing else got `enabled: true` over a history that was empty and
         * always would be — truthfully, because the reader really could reach the store, and
         * uselessly, because nothing was putting anything in it.
         */
        body.put("writing", DumIndexingLogWriters.isWriting());
        return body;
    }

    /**
     * A query that could not reach its store is a 503, not an empty page (roadmap
     * <strong>D75</strong>).
     *
     * <p>
     * Both engines used to catch the failure, log it where only an administrator would see it, and
     * answer HTTP 200 with an empty list — character for character what a log holding nothing
     * answers. An operator reading that screen concluded the connector had indexed nothing, when
     * the truth was that the connector had indexed everything and the store was down.
     */
    private ResponseStatusException storeUnavailable(String engine, Exception cause) {
        log.error("{} indexing log query failed", engine, cause);
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "The indexing log store (" + engine + ") could not be reached", cause);
    }

    /** Whether the configured store answers right now. Never throws: this endpoint is the probe. */
    private boolean reachable() {
        try {
            if (isMongoActive() && mongoClient != null) {
                mongoClient.getDatabase(databaseName).runCommand(new Document("ping", 1));
                return true;
            }
            // Borrowed and returned, not built and destroyed (D76): the pool is what makes this
            // endpoint cheap enough for a screen to poll.
            if (isRedisActive() && redisPool != null) {
                try (Jedis jedis = redisPool.getResource()) {
                    jedis.ping();
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Indexing log store '{}' is configured but did not answer: {}", loggingEngine,
                    e.getMessage());
        }
        return false;
    }

    /**
     * The indexing log, filtered.
     *
     * <p>
     * {@code source} and {@code transactionId} answer the two questions D69 and D71 made the rows
     * capable of answering (roadmap <strong>D72</strong>): what did this source do, and what
     * happened in this run. Every row carries both at every status, on both sides of the queue —
     * except rows written by a connector that predates the trail, which carry neither. Those are
     * returned whenever no filter names a source, because an unattributed row is a fact about an
     * old message rather than a reason to hide it.
     */
    @Operation(summary = "Indexing log entries (history)")
    @GetMapping("indexing")
    public Map<String, Object> indexingLogging(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contentId,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String transactionId,
            @RequestParam(defaultValue = "desc") String sort) {
        if (isMongoActive()) {
            return mongoIndexingDocuments(page, pageSize, dateFrom, dateTo,
                    status, contentId, resultStatus, url, source, transactionId, sort);
        }
        if (isRedisActive()) {
            return redisIndexingDocuments(page, pageSize, dateFrom, dateTo, status, contentId,
                    resultStatus, url, source, transactionId, sort);
        }
        return emptyPage(page, pageSize);
    }

    private boolean isMongoActive() {
        return ENGINE_MONGODB.equalsIgnoreCase(loggingEngine) && mongoEnabled;
    }

    private boolean isRedisActive() {
        return ENGINE_REDIS.equalsIgnoreCase(loggingEngine) && redisEnabled;
    }

    private Map<String, Object> emptyPage(int page, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(CONTENT, Collections.emptyList());
        result.put(PAGE, page);
        result.put(PAGE_SIZE, pageSize);
        result.put(TOTAL_ELEMENTS, 0L);
        result.put(TOTAL_PAGES, 0);
        result.put(UNREADABLE, 0);
        return result;
    }

    private Map<String, Object> mongoIndexingDocuments(int page, int pageSize,
            String dateFrom, String dateTo, String status, String contentId,
            String resultStatus, String url, String source, String transactionId, String sort) {
        Map<String, Object> result = emptyPage(page, pageSize);
        // The shared client (D76) — deliberately not in a try-with-resources: closing it here
        // would leave every later request querying a client in the closed state.
        if (mongoClient == null) {
            throw storeUnavailable("Mongo", new IllegalStateException("no usable MongoDB URI"));
        }
        try {
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            MongoCollection<Document> collection = database.getCollection(indexingCollectionName);

            Bson filter = indexingFilter(dateFrom, dateTo, status, contentId, resultStatus, url,
                    source, transactionId);

            long totalElements = collection.countDocuments(filter);
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);

            Bson sorting = "asc".equalsIgnoreCase(sort) ? Sorts.ascending(DATE) : Sorts.descending(DATE);
            int skip = page * pageSize;
            List<Document> documentList = new ArrayList<>();
            collection.find(filter)
                    .sort(sorting)
                    .skip(skip)
                    .limit(pageSize)
                    .forEach(documentList::add);

            result.put(CONTENT, documentList);
            result.put(TOTAL_ELEMENTS, totalElements);
            result.put(TOTAL_PAGES, totalPages);
        } catch (Exception e) {
            throw storeUnavailable("Mongo", e);
        }
        return result;
    }

    /**
     * The Redis path filters in Java after reading the whole list, so every filter here is a scan.
     * That was already true of its sort and its paging, so the seven the endpoint accepts (D73)
     * add no new class of problem — but it is why Mongo is the engine to pick when the log is
     * large enough to query rather than tail.
     *
     * <p>
     * All seven, deliberately. Four of them used to be accepted by the endpoint and never reach
     * this method, so a request naming one got the whole log back looking exactly like a correct
     * answer to a question that was never asked.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> redisIndexingDocuments(int page, int pageSize, String dateFrom,
            String dateTo, String status, String contentId, String resultStatus, String url,
            String source, String transactionId, String sort) {
        Map<String, Object> result = emptyPage(page, pageSize);
        Instant from = dateFrom == null || dateFrom.isBlank() ? null : parseDate(dateFrom, false);
        Instant to = dateTo == null || dateTo.isBlank() ? null : parseDate(dateTo, true);
        if (redisPool == null) {
            throw storeUnavailable("Redis", new IllegalStateException("no usable Redis URI"));
        }
        // Borrowed from the shared pool and returned by the close (D76), rather than a fresh
        // socket opened and thrown away per query.
        try (Jedis jedis = redisPool.getResource()) {
            // LRANGE reads newest-first (LPUSH order); reverse for ascending sort.
            List<String> raw = jedis.lrange(redisIndexingKey, 0, -1);
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> all = new ArrayList<>(raw.size());
            int unreadable = 0;
            for (String json : raw) {
                // D74: one row at a time. This parse used to sit under the query's own catch, so
                // a single row Jackson could not read discarded every row that came before it and
                // answered an empty page — indistinguishable, from the outside, from a log with
                // nothing in it.
                try {
                    JsonNode node = mapper.readTree(json);
                    if (matches(node, CONTENT_ID_FIELD, contentId)
                            && matches(node, SOURCE_FIELD, source)
                            && matches(node, TRANSACTION_ID_FIELD, transactionId)
                            && matches(node, "status", status)
                            && matches(node, "resultStatus", resultStatus)
                            && containsIgnoringCase(node, "url", url)
                            && withinRange(node, from, to)) {
                        all.add(mapper.convertValue(node, Map.class));
                    }
                } catch (Exception e) {
                    unreadable++;
                }
            }
            if ("asc".equalsIgnoreCase(sort)) {
                Collections.reverse(all);
            }
            reportUnreadable(unreadable, raw.size());
            int total = all.size();
            int totalPages = (int) Math.ceil((double) total / pageSize);
            int pageStart = Math.min(page * pageSize, total);
            int pageEnd = Math.min(pageStart + pageSize, total);
            result.put(CONTENT, all.subList(pageStart, pageEnd));
            result.put(TOTAL_ELEMENTS, (long) total);
            result.put(TOTAL_PAGES, totalPages);
            result.put(UNREADABLE, unreadable);
        } catch (Exception e) {
            throw storeUnavailable("Redis", e);
        }
        return result;
    }

    /**
     * Says once, loudly, that part of the log could not be read — and names the likeliest cause.
     *
     * <p>
     * A list written before D73 holds rows carrying the Mongo shell's {@code ISODate("…")}, which
     * no JSON reader accepts. They stay in the list until it rolls past {@code maxEntries}, so an
     * upgraded connector reads a log that is part old and part new, and the operator deserves to
     * be told which part they are missing rather than to infer it from a short count.
     */
    private void reportUnreadable(int unreadable, int scanned) {
        if (unreadable > 0) {
            log.warn("Indexing log: {} of {} Redis row(s) could not be read and were skipped. "
                    + "Rows written before 2026.3.6 are not valid JSON; they clear as the list "
                    + "rolls past its maxEntries, or delete the key to drop them now.",
                    unreadable, scanned);
        }
    }

    /**
     * Every filter the indexing log accepts, as one Mongo predicate — a pure function of the query
     * string, so what the endpoint will actually match is provable without a Mongo to match it
     * against. An absent parameter contributes no clause, so no filter at all means every row.
     */
    static Bson indexingFilter(String dateFrom, String dateTo, String status, String contentId,
            String resultStatus, String url, String source, String transactionId) {
        List<Bson> filters = new ArrayList<>();
        addDateRangeFilter(filters, dateFrom, dateTo);
        addExactFilter(filters, "status", status);
        addExactFilter(filters, CONTENT_ID_FIELD, contentId);
        addExactFilter(filters, "resultStatus", resultStatus);
        addRegexFilter(filters, "url", url);
        // D72: two more exact filters beside the five that were already here.
        addExactFilter(filters, SOURCE_FIELD, source);
        addExactFilter(filters, TRANSACTION_ID_FIELD, transactionId);
        return filters.isEmpty() ? new Document() : Filters.and(filters);
    }

    private static void addDateRangeFilter(List<Bson> filters, String dateFrom, String dateTo) {
        if (dateFrom != null && !dateFrom.isBlank()) {
            Instant from = parseDate(dateFrom, false);
            if (from != null) {
                filters.add(Filters.gte(DATE, from));
            }
        }
        if (dateTo != null && !dateTo.isBlank()) {
            Instant to = parseDate(dateTo, true);
            if (to != null) {
                filters.add(Filters.lte(DATE, to));
            }
        }
    }

    /**
     * Whether a Redis row satisfies one filter, where an absent filter matches everything.
     *
     * <p>
     * A row whose field is missing or null fails a filter that names a value — it is not that
     * source — but is returned untouched when no filter is given. Rows written before the trail
     * existed (D71) are exactly that shape, and dropping them from an unfiltered listing would
     * hide the history a reader came to read.
     */
    static boolean matches(JsonNode node, String field, String wanted) {
        if (wanted == null || wanted.isBlank()) {
            return true;
        }
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && wanted.equals(value.asString());
    }

    /**
     * The Redis counterpart of Mongo's case-insensitive url regex (D73) — a substring rather than
     * a pattern, because the endpoint's contract is "matches somewhere in the url" and a scan in
     * Java has no reason to compile one.
     */
    static boolean containsIgnoringCase(JsonNode node, String field, String wanted) {
        if (wanted == null || wanted.isBlank()) {
            return true;
        }
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && value.asString().toLowerCase(Locale.ROOT)
                .contains(wanted.toLowerCase(Locale.ROOT));
    }

    /**
     * Whether a Redis row falls in the requested date range (D73).
     *
     * <p>
     * The row's timestamp is the ISO-8601 string the serializer writes. A row whose date is
     * missing or unreadable fails a range that was asked for — it cannot be shown to be inside one
     * — and passes when no range was asked for at all.
     */
    static boolean withinRange(JsonNode node, Instant from, Instant to) {
        if (from == null && to == null) {
            return true;
        }
        JsonNode value = node.get(IsoDateSerializer.DATE_FIELD);
        if (value == null || value.isNull()) {
            return false;
        }
        try {
            Instant date = OffsetDateTime.parse(value.asString()).toInstant();
            return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
        } catch (Exception e) {
            return false;
        }
    }

    private static void addExactFilter(List<Bson> filters, String field, String value) {
        if (value != null && !value.isBlank()) {
            filters.add(Filters.eq(field, value));
        }
    }

    private static void addRegexFilter(List<Bson> filters, String field, String value) {
        if (value != null && !value.isBlank()) {
            filters.add(Filters.regex(field, value, "i"));
        }
    }

    private static Instant parseDate(String dateStr, boolean endOfDay) {
        try {
            LocalDate localDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            return endOfDay
                    ? localDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                    : localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            log.warn("Invalid date format: {}", dateStr);
            return null;
        }
    }
}
