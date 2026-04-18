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
 * Authentication privilege entity.
 *
 * @since 2026.2.14
 */
@Setter
@Getter
@Entity
@Table(name = "auth_privilege")
public class DumPrivilege implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @DumUuid
    @Column(updatable = false, nullable = false)
    private String id;

    private String name;

    @Column(length = 255)
    private String description;

    @Column(length = 50)
    private String category;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToMany(mappedBy = "dumPrivileges")
    private Collection<DumRole> dumRoles = new HashSet<>();

    public DumPrivilege() {
        super();
    }

    public DumPrivilege(String name) {
        super();
        this.name = name;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        return prime * result + ((getName() == null) ? 0 : getName().hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        DumPrivilege other = (DumPrivilege) obj;
        if (getName() == null) {
            return other.getName() == null;
        } else
            return getName().equals(other.getName());
    }

    @Override
    public String toString() {
        return "Privilege [name=" + name + "]" + "[id=" + id + "]";
    }
}
