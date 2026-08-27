/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.viglet.dumont.connector.api;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.viglet.dumont.commons.logging.IsoDateSerializer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
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
    }

    @Operation(summary = "Indexing log feature flag")
    @GetMapping("status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("engine", loggingEngine);
        body.put("enabled", isMongoActive() || isRedisActive());
        return body;
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
        try (MongoClient mongoClient = MongoClients.create(mongoUri)) {
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
            log.error("Mongo indexing log query failed", e);
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
        try (Jedis jedis = new Jedis(java.net.URI.create(redisUri))) {
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
            log.error("Redis indexing log query failed", e);
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
