/*
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

package com.viglet.dumont.connector.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.properties.DumAuthConfigProperties;
import com.viglet.dumont.connector.properties.DumAuthenticationProperty;

class DumDiscoveryApiTest {

    @Test
    void infoReportsFlagsWithNoAuthenticationConfigured() {
        DumAuthConfigProperties props = mock(DumAuthConfigProperties.class);
        when(props.isKeycloak()).thenReturn(true);
        when(props.getAuthentication()).thenReturn(null);
        DumDiscoveryApi api = new DumDiscoveryApi(props, Optional.empty());

        Map<String, Object> info = api.info();

        assertThat(info).containsEntry("product", "Viglet Dumont DEP")
                .containsEntry("keycloak", true)
                .containsEntry("authThirdparty", true)
                .containsEntry("selfRegistration", false)
                .containsEntry("multiTenant", false);
        assertThat((Iterable<?>) info.get("oauth2Providers")).isEmpty();
    }

    @Test
    void infoReadsAuthenticationSubProperties() {
        DumAuthConfigProperties props = mock(DumAuthConfigProperties.class);
        DumAuthenticationProperty auth = mock(DumAuthenticationProperty.class);
        when(props.isKeycloak()).thenReturn(false);
        when(props.getAuthentication()).thenReturn(auth);
        when(auth.isThirdparty()).thenReturn(false);
        when(auth.isNewUser()).thenReturn(true);
        DumDiscoveryApi api = new DumDiscoveryApi(props, Optional.empty());

        Map<String, Object> info = api.info();

        assertThat(info).containsEntry("authThirdparty", false)
                .containsEntry("selfRegistration", true);
    }
}
