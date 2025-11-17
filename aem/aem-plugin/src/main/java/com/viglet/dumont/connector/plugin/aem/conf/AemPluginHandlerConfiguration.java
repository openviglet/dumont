/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
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
package com.viglet.dumont.connector.plugin.aem.conf;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import com.viglet.dumont.connector.aem.commons.config.IAemConfiguration;
import com.viglet.dumont.connector.aem.commons.context.DumAemLocalePathContext;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AemPluginHandlerConfiguration implements IAemConfiguration {
    private final DumAemSource dumAemSource;
    private final Locale snLocale;
    private final String authorURLPrefix;
    private final String publishURLPrefix;
    private final String providerName;
    private final String oncePatternPath;
    private final String cmsHost;
    private final String cmsUsername;
    private final String cmsPassword;
    private final String cmsGroup;
    private final String cmsContentType;
    private final String cmsSubType;
    private final String cmsRootPath;
    private final boolean author;
    private final boolean publish;
    private final String authorSNSite;
    private final String publishSNSite;

    public AemPluginHandlerConfiguration(DumAemSource dumAemSource) {
        this.dumAemSource = dumAemSource;
        providerName = DEFAULT_PROVIDER;
        snLocale = dumAemSource.getDefaultLocale();
        authorURLPrefix = dumAemSource.getAuthorURLPrefix();
        publishURLPrefix = dumAemSource.getPublishURLPrefix();
        oncePatternPath = dumAemSource.getOncePattern();
        cmsHost = dumAemSource.getEndpoint();
        cmsUsername = dumAemSource.getUsername();
        cmsPassword = dumAemSource.getPassword();
        cmsGroup = dumAemSource.getName();
        cmsContentType = dumAemSource.getContentType();
        cmsSubType = dumAemSource.getSubType();
        cmsRootPath = dumAemSource.getRootPath();
        author = dumAemSource.isAuthor();
        publish = dumAemSource.isPublish();
        authorSNSite = dumAemSource.getAuthorSNSite();
        publishSNSite = dumAemSource.getPublishSNSite();
    }

    @Override
    public String getCmsHost() {
        return cmsHost;
    }

    @Override
    public String getCmsUsername() {
        return cmsUsername;
    }

    @Override
    public String getCmsPassword() {
        return cmsPassword;
    }

    @Override
    public String getCmsGroup() {
        return cmsGroup;
    }

    @Override
    public String getCmsContentType() {
        return cmsContentType;
    }

    @Override
    public String getCmsSubType() {
        return cmsSubType;
    }

    @Override
    public String getCmsRootPath() {
        return cmsRootPath;
    }

    @Override
    public String getAuthorURLPrefix() {
        return authorURLPrefix;
    }

    @Override
    public String getPublishURLPrefix() {
        return publishURLPrefix;
    }

    @Override
    public String getOncePatternPath() {
        return oncePatternPath;
    }

    @Override
    public Locale getDefaultLocale() {
        return snLocale;
    }

    public Collection<DumAemLocalePathContext> getLocales() {
        Collection<DumAemLocalePathContext> dumAemLocalePathContexts = new HashSet<>();
        dumAemSource.getLocalePaths().forEach(
                localePath -> dumAemLocalePathContexts.add(DumAemLocalePathContext.builder()
                        .path(localePath.getPath()).locale(localePath.getLocale()).build()));

        return dumAemLocalePathContexts;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public boolean isAuthor() {
        return author;
    }

    @Override
    public boolean isPublish() {
        return publish;
    }

    @Override
    public String getAuthorSNSite() {
        return authorSNSite;
    }

    @Override
    public String getPublishSNSite() {
        return publishSNSite;
    }
}
