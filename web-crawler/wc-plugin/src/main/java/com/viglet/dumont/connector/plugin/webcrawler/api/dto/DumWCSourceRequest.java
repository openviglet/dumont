/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viglet.dumont.connector.plugin.webcrawler.api.dto;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCAllowUrl;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCAttributeMapping;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCFileExtension;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCNotAllowUrl;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCStartingPoint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for {@code DumWCSourceApi} create/update (SonarQube S4684) — an aggregate POJO
 * mirroring the writable fields of {@code DumWCSource} so the persistent root entity is never bound
 * directly from the request body. The child collections stay typed as their config sub-entities
 * (intra-aggregate rows the controller re-parents on save); only the root binding is decoupled.
 * {@code id} is accepted only to validate the path variable on update.
 */
@Getter
@Setter
@NoArgsConstructor
public class DumWCSourceRequest {

    private String id;
    private String title;
    private String description;
    private Locale locale;
    private String localeClass;
    private String url;
    private String username;
    private String password;
    private Boolean enableSitemap;
    private Integer crawlDelay;
    private String fetcher;
    private String userAgentStrategy;
    private Integer workers;
    private Collection<String> turSNSites = new HashSet<>();
    private Collection<DumWCStartingPoint> startingPoints = new HashSet<>();
    private Collection<DumWCAllowUrl> allowUrls = new HashSet<>();
    private Collection<DumWCNotAllowUrl> notAllowUrls = new HashSet<>();
    private Collection<DumWCFileExtension> notAllowExtensions = new HashSet<>();
    private Collection<DumWCAttributeMapping> attributeMappings = new HashSet<>();
}
