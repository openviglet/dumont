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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Authentication role entity.
 *
 * @since 2026.2.14
 */
@Getter
@Entity
@Table(name = "auth_role")
public class DumRole implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Setter
    @Id
    @DumUuid
    @Column(updatable = false, nullable = false)
    private String id;

    @Setter
    @Column(nullable = false, length = 50)
    private String name;

    @Setter
    @Column
    private String description;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToMany(mappedBy = "dumRoles")
    private Collection<DumGroup> dumGroups = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "auth_roles_privileges",
            joinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "privilege_id", referencedColumnName = "id"))
    private Collection<DumPrivilege> dumPrivileges = new HashSet<>();

    public DumRole() {
        super();
    }

    public DumRole(String name) {
        this.name = name;
    }

    public void setDumPrivileges(Collection<DumPrivilege> dumPrivileges) {
        this.dumPrivileges.clear();
        if (dumPrivileges != null) {
            this.dumPrivileges.addAll(dumPrivileges);
        }
    }
}
