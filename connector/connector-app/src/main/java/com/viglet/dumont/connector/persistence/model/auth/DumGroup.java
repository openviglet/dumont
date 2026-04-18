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
import java.util.Collection;
import java.util.HashSet;

import com.viglet.dumont.spring.jpa.DumUuid;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Authentication group entity.
 *
 * @since 2026.2.14
 */
@Getter
@Entity
@Table(name = "auth_group")
public class DumGroup implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Setter
    @Id
    @DumUuid
    @Column(updatable = false, nullable = false)
    private String id;

    @Setter
    private String name;

    @Setter
    private String description;

    @ManyToMany
    private Collection<DumRole> dumRoles = new HashSet<>();

    @ManyToMany(mappedBy = "dumGroups")
    private Collection<DumUser> dumUsers = new HashSet<>();

    public void setDumUsers(Collection<DumUser> dumUsers) {
        this.dumUsers.clear();
        if (dumUsers != null) {
            this.dumUsers.addAll(dumUsers);
        }
    }

    public void setDumRoles(Collection<DumRole> dumRoles) {
        this.dumRoles.clear();
        if (dumRoles != null) {
            this.dumRoles.addAll(dumRoles);
        }
    }
}
