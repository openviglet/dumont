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
 * Status of a user's membership in a tenant (T398 / §XIV.9). Only
 * {@link #ACTIVE} grants access; {@code DumTenantFilter} rejects everything
 * else with 403.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum DumTenantMembershipStatus {
    ACTIVE,
    INVITED,
    SUSPENDED
}
