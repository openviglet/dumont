/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.persistence.model.auth;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Authentication user entity.
 *
 * @since 2026.2.14
 */
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Entity
@Table(name = "auth_user")
public class DumUser implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Setter
    @Id
    private String username;

    @Setter
    private String email;

    @Setter
    private String firstName;

    @Setter
    private Instant lastLogin;

    @Setter
    private String lastName;

    @Setter
    private String password;

    @Setter
    private String realm;

    @Setter
    private int enabled;

    @Setter
    private String avatarUrl;

    @Builder.Default
    @ManyToMany
    private Collection<DumGroup> dumGroups = new HashSet<>();

    public DumUser(DumUser user) {
        this.username = user.username;
        this.email = user.email;
        this.password = user.password;
        this.enabled = user.enabled;
    }

    public void setDumGroups(Collection<DumGroup> dumGroups) {
        this.dumGroups.clear();
        if (dumGroups != null) {
            this.dumGroups.addAll(dumGroups);
        }
    }
}
