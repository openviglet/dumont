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
 * Lifecycle state of a tenant (T398 / §XIV.9, Turing/Shio parity). A
 * {@code SUSPENDED} tenant keeps its data but its members are blocked at
 * {@code DumTenantFilter} with HTTP 403.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum DumTenantStatus {
    ACTIVE,
    SUSPENDED
}
