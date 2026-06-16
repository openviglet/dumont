/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.persistence.model.tenant;

/**
 * A principal's role within a tenant (T398 / §XIV.9, mirrors
 * {@code VigletTenantRole} and the Turing/Shio role sets).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum DumTenantRole {
    OWNER,
    ADMIN,
    MEMBER
}
