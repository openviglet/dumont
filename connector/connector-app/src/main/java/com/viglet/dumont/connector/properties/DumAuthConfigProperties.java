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
    private boolean permissions = true;
    private boolean keycloak = false;
    private String keycloakAdminId;
    private DumAuthenticationProperty authentication = new DumAuthenticationProperty();
}
