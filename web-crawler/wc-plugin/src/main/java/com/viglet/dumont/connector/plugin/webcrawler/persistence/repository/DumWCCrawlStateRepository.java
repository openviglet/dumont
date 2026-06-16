/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCCrawlState;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCSource;

/** Per-(source, url) conditional-fetch state for incremental re-crawls (roadmap D12). */
public interface DumWCCrawlStateRepository extends JpaRepository<DumWCCrawlState, String> {

    List<DumWCCrawlState> findByDumWCSource(DumWCSource dumWCSource);

    Optional<DumWCCrawlState> findByDumWCSourceAndUrl(DumWCSource dumWCSource, String url);

    int deleteByDumWCSource(DumWCSource dumWCSource);
}
