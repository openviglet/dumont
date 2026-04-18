/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.config.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Enriches OIDC users (Keycloak with scope=openid) with authorities from
 * the local database.
 *
 * @since 2026.2.14
 */
@Service
public class DumOidcUserService extends OidcUserService {

    private static final String PREFERRED_USERNAME = "preferred_username";
    private static final String EMAIL = "email";

    private final DumAuthorityResolver dumAuthorityResolver;

    public DumOidcUserService(DumAuthorityResolver dumAuthorityResolver) {
        this.dumAuthorityResolver = dumAuthorityResolver;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String username = oidcUser.getAttribute(PREFERRED_USERNAME);
        if (username == null) {
            username = oidcUser.getAttribute(EMAIL);
        }

        Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
        dumAuthorityResolver.resolve(username, authorities);

        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }
}
