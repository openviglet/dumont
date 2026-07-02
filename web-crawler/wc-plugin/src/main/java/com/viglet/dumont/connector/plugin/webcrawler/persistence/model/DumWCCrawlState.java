/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler.persistence.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-(source, url) conditional-fetch bookkeeping for incremental re-crawls (roadmap D12). Records
 * the HTTP validators last seen for a page so the next crawl can revalidate cheaply instead of
 * re-downloading:
 *
 * <ul>
 *   <li><strong>HTTP-conditional GET</strong>: {@code etag} / {@code lastModified} are sent back as
 *       {@code If-None-Match} / {@code If-Modified-Since}; a {@code 304 Not Modified} ends the work
 *       for the page with no body transfer.</li>
 *   <li><strong>Sitemap pre-fetch skip</strong>: {@code sitemapLastmod} holds the {@code <lastmod>}
 *       last observed in {@code sitemap.xml} (D06); an unchanged value skips the URL before any
 *       fetch.</li>
 * </ul>
 *
 * <p>
 * {@code checksum} mirrors the checksum the crawler last published for the page, so a skipped page
 * can be re-emitted as <em>unchanged</em> through the indexing pipeline — refreshing its bookkeeping
 * (and surviving the safe-run deindex pass) without a network round-trip. This is additive state
 * alongside the existing checksum reindex/skip path, never a replacement for it.
 *
 * <p>
 * The (source, url) pair is unique and the source column is indexed for the per-crawl bulk load.
 */
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wc_crawl_state",
        uniqueConstraints = @UniqueConstraint(columnNames = { "wc_source_id", "url" }),
        indexes = @Index(name = "idx_wc_crawl_state_source", columnList = "wc_source_id"))
@JsonIgnoreProperties({ "dumWCSource" })
public class DumWCCrawlState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne
    @JoinColumn(name = "wc_source_id", nullable = false)
    private DumWCSource dumWCSource;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "etag", length = 1024)
    private String etag;

    @Column(name = "last_modified")
    private String lastModified;

    @Column(name = "sitemap_lastmod")
    private String sitemapLastmod;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;
}
