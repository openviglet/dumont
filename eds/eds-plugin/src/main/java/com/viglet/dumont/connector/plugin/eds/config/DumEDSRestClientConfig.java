/*
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

package com.viglet.dumont.connector.plugin.eds.config;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/**
 * Provides a {@link RestClient.Builder} bean for the EDS plugin.
 *
 * <p>Spring Boot 4 split web-client auto-configuration into the separate
 * {@code spring-boot-restclient} module, which is not on the connector
 * classpath. As a result the auto-configured {@code RestClient.Builder} bean is
 * absent, and EDS clients that constructor-inject it
 * ({@code DumEDSIndexClient}, {@code DumEDSSitemapClient}) cannot start. This
 * keeps the plugin self-contained.
 *
 * <p>The builder is {@code prototype}-scoped so each consumer receives a fresh,
 * isolated instance — both clients mutate it via {@code defaultHeader(...)},
 * which would otherwise leak across consumers if a singleton were shared.
 */
@Configuration
public class DumEDSRestClientConfig {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @ConditionalOnMissingBean
    RestClient.Builder edsRestClientBuilder() {
        return RestClient.builder();
    }
}
