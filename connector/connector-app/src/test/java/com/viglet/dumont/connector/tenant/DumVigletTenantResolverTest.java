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

package com.viglet.dumont.connector.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.viglet.core.tenancy.VigletTenantResolver.Resolution;

class DumVigletTenantResolverTest {

    private final DumVigletTenantResolver resolver = new DumVigletTenantResolver();

    @Test
    void resolvesFromSessionAttributeFirst() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("DUM_TENANT", "acme");

        Resolution resolution = resolver.resolve(request, null);

        assertThat(resolution).isNotNull();
    }

    @Test
    void resolvesFromHeaderWhenNoSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Dumont-Tenant", "acme");

        assertThat(resolver.resolve(request, null)).isNotNull();
    }

    @Test
    void resolvesFromOAuth2ClaimWhenNoSessionOrHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        OAuth2User principal = mock(OAuth2User.class);
        when(principal.getAttributes()).thenReturn(Map.of("tenant", "acme"));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);

        assertThat(resolver.resolve(request, authentication)).isNotNull();
    }

    @Test
    void returnsResolutionWhenNothingMatches() {
        assertThat(resolver.resolve(new MockHttpServletRequest(), null)).isNotNull();
    }
}
