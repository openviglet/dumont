/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.dumont.connector.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingRuleModel;
import com.viglet.dumont.connector.service.DumConnectorIndexingRuleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 * @since 2025.2
 */

@RestController
@RequestMapping("/api/v2/connector/indexing-rule")
@Tag(name = "Connector Indexing Rules", description = "Connector Indexing Rules API")
public class DumConnectorIndexingRuleAPI {
    private final DumConnectorIndexingRuleService indexingRuleService;

    public DumConnectorIndexingRuleAPI(DumConnectorIndexingRuleService dumConnectorIndexingRuleService) {
        this.indexingRuleService = dumConnectorIndexingRuleService;
    }

    @Operation(summary = "Connector Indexing Rule List By Source")
    @GetMapping("source/{source}")
    public Set<DumConnectorIndexingRuleModel> dumConnectorIndexingRuleBySourceList(@PathVariable String source) {
        return indexingRuleService.getBySource(source);
    }

    @Operation(summary = "Connector Indexing Rule List")
    @GetMapping()
    public List<DumConnectorIndexingRuleModel> dumConnectorIndexingRuleList() {
        return indexingRuleService.getAll();
    }

    @Operation(summary = "Show a Connector Indexing Rules")
    @GetMapping("{id}")
    public DumConnectorIndexingRuleModel dumConnectorIndexingRuleGet(@PathVariable String id) {
        return indexingRuleService.getById(id)
                .orElse(new DumConnectorIndexingRuleModel());
    }

    @Operation(summary = "Update a Connector Indexing Rules")
    @PutMapping("/{id}")
    public DumConnectorIndexingRuleModel dumConnectorIndexingRuleUpdate(@PathVariable String id,
            @RequestBody DumConnectorIndexingRuleModel indexingRule) {
        return indexingRuleService.update(id, indexingRule);
    }

    @Transactional
    @Operation(summary = "Delete a Connector Indexing Rules")
    @DeleteMapping("/{id}")
    public boolean dumConnectorIndexingRuleDelete(@PathVariable String id) {
        indexingRuleService.deleteById(id);
        return true;
    }

    @Operation(summary = "Create a Connector Ranking Expression")
    @PostMapping
    public DumConnectorIndexingRuleModel dumConnectorIndexingRuleAdd(
            @RequestBody DumConnectorIndexingRuleModel dumConnectorIndexingRule) {
        dumConnectorIndexingRule.setLastModifiedDate(Instant.now());
        return indexingRuleService.save(dumConnectorIndexingRule);
    }

    @Operation(summary = "Connector Ranking Expression Structure")
    @GetMapping("structure")
    public DumConnectorIndexingRuleModel dumConnectorIndexingRuleStructure() {
        return new DumConnectorIndexingRuleModel();

    }
}