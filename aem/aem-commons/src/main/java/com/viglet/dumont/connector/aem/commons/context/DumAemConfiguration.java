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

package com.viglet.dumont.connector.aem.commons.context;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import com.viglet.dumont.connector.aem.commons.bean.DumAemFetchStrategy;
import com.viglet.dumont.connector.aem.commons.config.IAemConfiguration;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Slf4j
public class DumAemConfiguration {
  private String id;
  private String url;
  private String username;
  private String password;
  private String rootPath;

  private String subType;

  private String siteName;
  private Locale defaultLocale;
  private String providerName;
  private String authorURLPrefix;
  private String publishURLPrefix;
  private String oncePattern;
  private boolean author;
  private boolean publish;
  private String authorSNSite;
  private String publishSNSite;

  private String contentType;
  private boolean queryBuilderEnabled;

  /** How this source's content is fetched (roadmap D61); never null once built. */
  @Builder.Default
  private DumAemFetchStrategy fetchStrategy = DumAemFetchStrategy.SLING;
  /** The {@code delivery-pXXXX-eYYYY} host, when the delivery strategy uses a dedicated one. */
  private String deliveryURLPrefix;
  /**
   * Overrides the Assets Search API path on the delivery host (roadmap D67). Blank uses the
   * default; set it when the instance mounts that API somewhere else.
   */
  private String assetSearchPath;

  @Builder.Default
  private Collection<DumAemLocalePathContext> localePaths = new HashSet<>();

  /**
   * Whether content is reached through the publish-tier delivery APIs rather than the author-tier
   * Sling servlets (roadmap <strong>D61</strong>).
   */
  public boolean isDeliveryFetch() {
    return fetchStrategy == DumAemFetchStrategy.DELIVERY;
  }

  /** The copy constructors bypass the builder default, so a null strategy is normalised here. */
  private static DumAemFetchStrategy defaulted(DumAemFetchStrategy strategy) {
    return strategy != null ? strategy : DumAemFetchStrategy.SLING;
  }

  public DumAemConfiguration(DumAemConfiguration dumAemConfiguration) {
    this.id = dumAemConfiguration.getId();
    this.url = dumAemConfiguration.getUrl();
    this.username = dumAemConfiguration.getUsername();
    this.password = dumAemConfiguration.getPassword();
    this.rootPath = dumAemConfiguration.getRootPath();
    this.contentType = dumAemConfiguration.getContentType();
    this.subType = dumAemConfiguration.getSubType();

    this.siteName = dumAemConfiguration.getSiteName();
    this.defaultLocale = dumAemConfiguration.getDefaultLocale();
    this.providerName = dumAemConfiguration.getProviderName();
    this.authorURLPrefix = dumAemConfiguration.getAuthorURLPrefix();
    this.publishURLPrefix = dumAemConfiguration.getPublishURLPrefix();
    this.oncePattern = dumAemConfiguration.getOncePattern();
    this.localePaths = dumAemConfiguration.getLocalePaths();
    this.author = dumAemConfiguration.isAuthor();
    this.publish = dumAemConfiguration.isPublish();
    this.authorSNSite = dumAemConfiguration.getAuthorSNSite();
    this.publishSNSite = dumAemConfiguration.getPublishSNSite();
    this.queryBuilderEnabled = dumAemConfiguration.isQueryBuilderEnabled();
    this.fetchStrategy = defaulted(dumAemConfiguration.getFetchStrategy());
    this.deliveryURLPrefix = dumAemConfiguration.getDeliveryURLPrefix();
    this.assetSearchPath = dumAemConfiguration.getAssetSearchPath();
  }

  public DumAemConfiguration(IAemConfiguration iaemConfiguration) {

    this.id = iaemConfiguration.getCmsGroup();
    this.contentType = iaemConfiguration.getCmsContentType();
    this.defaultLocale = iaemConfiguration.getDefaultLocale();
    this.rootPath = iaemConfiguration.getCmsRootPath();
    this.url = iaemConfiguration.getCmsHost();
    this.authorURLPrefix = iaemConfiguration.getAuthorURLPrefix();
    this.publishURLPrefix = iaemConfiguration.getPublishURLPrefix();
    this.subType = iaemConfiguration.getCmsSubType();
    this.oncePattern = iaemConfiguration.getOncePatternPath();
    this.providerName = iaemConfiguration.getProviderName();
    this.password = iaemConfiguration.getCmsPassword();
    this.username = iaemConfiguration.getCmsUsername();
    this.localePaths = iaemConfiguration.getLocales();
    this.author = iaemConfiguration.isAuthor();
    this.publish = iaemConfiguration.isPublish();
    this.authorSNSite = iaemConfiguration.getAuthorSNSite();
    this.publishSNSite = iaemConfiguration.getPublishSNSite();
    this.fetchStrategy = defaulted(iaemConfiguration.getFetchStrategy());
    this.deliveryURLPrefix = iaemConfiguration.getDeliveryURLPrefix();

    DumAemCommonsUtils.getInfinityJson(iaemConfiguration.getCmsRootPath(), this, false)
        .flatMap(infinityJson -> DumAemCommonsUtils.getSiteName(this,
            infinityJson))
        .ifPresent(name -> this.siteName = name);

  }

}
