/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.persistence.repository.auth;

import java.util.Collection;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.dumont.connector.persistence.model.auth.DumGroup;
import com.viglet.dumont.connector.persistence.model.auth.DumUser;

public interface DumUserRepository extends JpaRepository<DumUser, String> {
    DumUser findByUsername(String username);
    Set<DumUser> findByDumGroupsIn(Collection<DumGroup> groups);
    void deleteByUsername(String username);
}
