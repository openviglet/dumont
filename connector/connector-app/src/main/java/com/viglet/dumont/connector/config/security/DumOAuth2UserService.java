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
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Enriches OAuth2 (non-OIDC) users with authorities resolved from the local database.
 *
 * @since 2026.2.14
 */
@Service
public class DumOAuth2UserService extends DefaultOAuth2UserService {

    private static final String PREFERRED_USERNAME = "preferred_username";
    private static final String LOGIN = "login";
    private static final String EMAIL = "email";

    private final DumAuthorityResolver dumAuthorityResolver;

    public DumOAuth2UserService(DumAuthorityResolver dumAuthorityResolver) {
        this.dumAuthorityResolver = dumAuthorityResolver;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String username = oAuth2User.getAttribute(PREFERRED_USERNAME);
        if (username == null) {
            username = oAuth2User.getAttribute(LOGIN);
        }
        if (username == null) {
            username = oAuth2User.getAttribute(EMAIL);
        }

        Set<GrantedAuthority> authorities = new HashSet<>(oAuth2User.getAuthorities());
        dumAuthorityResolver.resolve(username, authorities);

        return new DefaultOAuth2User(authorities, oAuth2User.getAttributes(),
                userRequest.getClientRegistration().getProviderDetails()
                        .getUserInfoEndpoint().getUserNameAttributeName());
    }
}
