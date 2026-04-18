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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.dumont.connector.persistence.model.auth.DumPrivilege;

@Repository
public interface DumPrivilegeRepository extends JpaRepository<DumPrivilege, String> {
    DumPrivilege findByName(String name);
}
