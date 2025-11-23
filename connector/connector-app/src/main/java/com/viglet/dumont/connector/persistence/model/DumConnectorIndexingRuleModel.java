/*
 * Copyright (C) 2016-2023 the original author or authors.
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

package com.viglet.dumont.connector.persistence.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hibernate.annotations.UuidGenerator;

import com.viglet.dumont.connector.commons.DumConnectorIndexingRuleType;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The persistent class for the dum_indexing_rule database table.
 *
 * @author Alexandre Oliveira
 * @since 2025.2
 */

@Builder
@RequiredArgsConstructor
@Accessors(chain = true)
@Setter
@Getter
@Entity
@Table(name = "dum_indexing_rule", uniqueConstraints = { @UniqueConstraint(columnNames = { "id" }) })
@AllArgsConstructor
public class DumConnectorIndexingRuleModel implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(length = 50)
    private String name;

    @Column
    private String description;

    private String source;

    @Enumerated(EnumType.STRING)
    @Column
    private DumConnectorIndexingRuleType ruleType;

    @Column
    private String attribute;

    @Temporal(TemporalType.TIMESTAMP)
    @Column
    private Date lastModifiedDate;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "con_indexing_rule_values", joinColumns = @JoinColumn(name = "id"))
    @Column
    private List<String> values = new ArrayList<>();

}