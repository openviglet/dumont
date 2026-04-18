/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.bean;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Payload returned by {@code /api/v2/user/current} and {@code /api/login}.
 * Mirrors {@code TurCurrentUser} on the Turing side so the shared frontend
 * components can consume either product's response unchanged.
 *
 * @since 2026.2.14
 */
@Setter
@Getter
public class DumCurrentUser {
    private String username;
    private String firstName;
    private String lastName;
    private boolean admin;
    private String email;
    private boolean hasAvatar;
    private String avatarUrl;
    private String realm;
    private List<String> privileges = new ArrayList<>();
}
