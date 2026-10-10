/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Authentication / permission configuration properties for dumont.
 * Defines whether the runtime enforces permissions, whether Keycloak is
 * used, and the third-party authentication options, mirroring the
 * {@code TurConfigProperties} contract on the Turing side.
 *
 * @since 2026.2.14
 */
@Getter
@Setter
@Component
@ConfigurationProperties("dumont")
public class DumAuthConfigProperties {
    /**
     * Master switch for multi-tenancy (T398 / §XIV.9). When {@code true}, the
     * shared {@code viglet-core-tenancy} resolution filter + {@code @TenantId} row
     * isolation are active; when {@code false} (default) Dumont behaves as a single
     * tenant ({@code dumont}) and the filter is a no-op.
     */
    private boolean multiTenant = false;
    private boolean permissions = true;
    private boolean keycloak = false;
    private String keycloakAdminId;
    private DumAuthenticationProperty authentication = new DumAuthenticationProperty();
    /**
     * Client Silos isolation (Viglet Cloud C051). When set (e.g. {@code vilt}),
     * this instance is a dedicated per-client silo and only admits logins/tokens
     * whose {@code viglet_client} claim equals this value; any other client (or no
     * claim) is rejected. Blank/unset (default) = shared instance, no restriction.
     * Env: {@code DUMONT_REQUIRED_VIGLET_CLIENT}.
     */
    private String requiredVigletClient;
    /**
     * Client Silos (Viglet Cloud). Comma-separated usernames/e-mails granted full
     * admin (the {@code Administrator} group) on this instance, seeded on startup
     * like {@link #keycloakAdminId}. Lets a dedicated silo give its client's
     * operators total autonomy. Matches the login username (the e-mail when
     * {@code registrationEmailAsUsername} is on). Env: {@code DUMONT_ADMIN_EMAILS}.
     */
    private String adminEmails;
    /**
     * The local {@code admin} account (roadmap D93). Env: {@code DUMONT_ADMIN_PASSWORD}.
     */
    private Admin admin = new Admin();

    @Getter
    @Setter
    public static class Admin {
        /**
         * Applied at boot while the {@code admin} account has no password, so a compose file or CI
         * starts with a usable login and the first-access setup screen is skipped. Ignored once a
         * password is stored. Env: {@code DUMONT_ADMIN_PASSWORD}.
         */
        private String password;
    }
}
