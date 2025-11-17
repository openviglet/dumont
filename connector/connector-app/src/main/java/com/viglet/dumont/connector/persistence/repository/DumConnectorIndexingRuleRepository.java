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
package com.viglet.dumont.connector.persistence.repository;

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.dumont.connector.commons.DumConnectorIndexingRuleType;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingRuleModel;

/**
 * @author Alexandre Oliveira
 * @since 2025.2
 */
public interface DumConnectorIndexingRuleRepository extends JpaRepository<DumConnectorIndexingRuleModel, String> {
	Set<DumConnectorIndexingRuleModel> findBySource(Sort language, String source);

	Set<DumConnectorIndexingRuleModel> findBySourceAndRuleType(String source, DumConnectorIndexingRuleType type);

	void delete(@NotNull DumConnectorIndexingRuleModel dumConnectorIndexingRule);
}
