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

import java.io.Serial;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import com.viglet.dumont.connector.persistence.model.auth.DumUser;

/**
 * Spring Security {@link UserDetails} backed by {@link DumUser}.
 *
 * @since 2026.2.14
 */
public class DumCustomUserDetails extends DumUser implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;
    private final List<String> dumUserRoles;

    public DumCustomUserDetails(DumUser dumUser, List<String> dumUserRoles) {
        super(dumUser);
        this.dumUserRoles = dumUserRoles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String roles = StringUtils.collectionToCommaDelimitedString(dumUserRoles);
        return AuthorityUtils.commaSeparatedStringToAuthorityList(roles);
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
