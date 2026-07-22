/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.config.security;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/**
 * Client Silos isolation gate (Viglet Cloud C051). Enforces, at login time, that
 * the authenticated user's {@code viglet_client} claim matches the client this
 * instance is dedicated to (see {@code dumont.required-viglet-client}).
 *
 * <p>Closes the browser (OAuth2 authorization-code) hole the nginx bearer-token
 * gate cannot cover: browser navigation carries an app session cookie, not a JWT,
 * so a non-member could otherwise complete an interactive login at another
 * client's dedicated host. No-op when the required client is blank/unset.
 *
 * @since 2026.2
 */
public final class DumClientClaimGate {

    public static final String VIGLET_CLIENT_CLAIM = "viglet_client";

    private DumClientClaimGate() {
    }

    public static void enforce(String requiredClient, Object actualClaim) {
        if (requiredClient == null || requiredClient.isBlank()) {
            return;
        }
        String actual = actualClaim == null ? null : actualClaim.toString();
        if (!requiredClient.equals(actual)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            OAuth2ErrorCodes.ACCESS_DENIED,
                            "This instance is restricted to the '" + requiredClient + "' client.",
                            null),
                    "viglet_client claim '" + actual + "' does not match required '" + requiredClient + "'");
        }
    }
}
