/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */

package com.viglet.dumont.connector.api.dto;

import java.util.ArrayList;
import java.util.List;

import com.viglet.dumont.connector.commons.DumConnectorIndexingRuleType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for {@code DumConnectorIndexingRuleAPI} create/update (SonarQube S4684) — a plain
 * POJO mirroring the writable fields of {@code DumConnectorIndexingRuleModel} so the persistent
 * entity is never bound directly from the request body. {@code lastModifiedDate} is stamped
 * server-side and {@code id} is generated, so neither is accepted here for create.
 */
@Getter
@Setter
@NoArgsConstructor
public class DumConnectorIndexingRuleRequest {

    private String id;
    private String name;
    private String description;
    private String source;
    private DumConnectorIndexingRuleType ruleType;
    private String attribute;
    private List<String> values = new ArrayList<>();
}
