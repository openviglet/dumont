/*
 * Copyright (C) 2016-2023 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.dumont.connector.plugin.aem.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.viglet.dumont.commons.cache.DumCustomClassCache;
import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtDeltaDate;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtDeltaDateInterface;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemContentMapping;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DumAemContentDefinitionService {
        private final DumAemContentMappingService dumAemContentMappingService;

        public DumAemContentDefinitionService(
                        DumAemContentMappingService dumAemContentMappingService) {
                this.dumAemContentMappingService = dumAemContentMappingService;
        }

        public List<TurSNAttributeSpec> getAttributeSpec(
                        DumAemContentMapping dumAemContentMapping) {
                return Optional.ofNullable(dumAemContentMapping)
                                .map(DumAemContentMapping::getTargetAttrDefinitions)
                                .orElse(new ArrayList<>());

        }

        public String getDeltaClassName(DumAemContentMapping dumAemContentMapping) {
                return Optional.ofNullable(dumAemContentMapping)
                                .map(DumAemContentMapping::getDeltaClassName).orElse(null);
        }

        public Date getDeltaDate(DumAemObject aemObject,
                        DumAemConfiguration dumAemSourceContext,
                        DumAemContentMapping dumAemContentMapping) {
                Date deltaDate = Optional.ofNullable(getDeltaClassName(dumAemContentMapping)).map(
                                className -> DumCustomClassCache.getCustomClassMap(className).map(
                                                classInstance -> ((DumAemExtDeltaDateInterface) classInstance)
                                                                .consume(aemObject,
                                                                                dumAemSourceContext))
                                                .orElseGet(() -> defaultDeltaDate(aemObject,
                                                                dumAemSourceContext)))
                                .orElseGet(() -> defaultDeltaDate(aemObject, dumAemSourceContext));
                log.debug("Delta Date {} from {}", deltaDate.toString(), aemObject.getPath());
                return deltaDate;
        }

        private Date defaultDeltaDate(DumAemObject aemObject,
                        DumAemConfiguration dumAemSourceContext) {
                return new DumAemExtDeltaDate().consume(aemObject, dumAemSourceContext);
        }

        public Optional<DumAemModel> getModel(DumAemConfiguration dumAemConfiguration,
                        DumAemSource dumAemSource) {
                DumAemContentMapping dumAemContentMapping = dumAemContentMappingService
                                .getDumAemContentMapping(dumAemSource);
                return Optional.ofNullable(dumAemContentMapping)
                                .flatMap(dumCmsContentMapping -> getModel(dumAemConfiguration,
                                                dumCmsContentMapping));
        }

        private Optional<DumAemModel> getModel(DumAemConfiguration dumAemConfiguration,
                        DumAemContentMapping dumCmsContentMapping) {
                return getModel(dumCmsContentMapping.getModels(),
                                dumAemConfiguration.getContentType())
                                .map(model -> getDumCmsTargetAttrs(dumCmsContentMapping, model));
        }

        private DumAemModel getDumCmsTargetAttrs(DumAemContentMapping dumCmsContentMapping,
                        DumAemModel model) {
                List<DumAemTargetAttr> dumCmsTargetAttrs = new ArrayList<>(
                                addTargetAttrFromDefinition(model, dumCmsContentMapping));
                model.getTargetAttrs().forEach(dumCmsTargetAttr -> {
                        if (dumCmsTargetAttrs.stream().noneMatch(
                                        o -> o.getName().equals(dumCmsTargetAttr.getName())))
                                dumCmsTargetAttrs.add(dumCmsTargetAttr);
                });
                model.setTargetAttrs(dumCmsTargetAttrs);
                return model;
        }

        private Optional<DumAemModel> getModel(final List<DumAemModel> dumCmsModels,
                        final String name) {
                return dumCmsModels != null ? dumCmsModels.stream()
                                .filter(o -> o != null && o.getType().equals(name)).findFirst()
                                : Optional.empty();
        }

        private List<DumAemTargetAttr> addTargetAttrFromDefinition(DumAemModel model,
                        DumAemContentMapping dumCmsContentMapping) {
                List<DumAemTargetAttr> dumCmsTargetAttrs = new ArrayList<>();
                dumCmsContentMapping.getTargetAttrDefinitions()
                                .forEach(targetAttrDefinition -> addTargetAttr(model,
                                                dumCmsTargetAttrs,
                                                targetAttrDefinition));

                return dumCmsTargetAttrs;
        }

        private void addTargetAttr(DumAemModel model, List<DumAemTargetAttr> dumAemTargetAttr,
                        TurSNAttributeSpec attributeSpec) {
                getTargetAttr(model.getTargetAttrs(), attributeSpec.getName())
                                .ifPresentOrElse(
                                                targetAttr -> addTargetAttr(dumAemTargetAttr,
                                                                attributeSpec,
                                                                targetAttr),
                                                () -> addTargetAttrMandatory(dumAemTargetAttr,
                                                                attributeSpec));
        }

        private void addTargetAttrMandatory(List<DumAemTargetAttr> dumCmsTargetAttrs,
                        TurSNAttributeSpec targetAttrDefinition) {
                if (targetAttrDefinition.isMandatory()) {
                        dumCmsTargetAttrs.add(setTargetAttrFromDefinition(targetAttrDefinition,
                                        new DumAemTargetAttr()));
                }
        }

        private boolean addTargetAttr(List<DumAemTargetAttr> dumCmsTargetAttrs,
                        TurSNAttributeSpec targetAttrDefinition, DumAemTargetAttr targetAttr) {
                return dumCmsTargetAttrs
                                .add(setTargetAttrFromDefinition(targetAttrDefinition, targetAttr));
        }

        private Optional<DumAemTargetAttr> getTargetAttr(
                        final List<DumAemTargetAttr> dumCmsTargetAttrs, final String name) {
                return dumCmsTargetAttrs.stream().filter(o -> o.getName().equals(name)).findFirst();
        }

        private DumAemTargetAttr setTargetAttrFromDefinition(TurSNAttributeSpec dumSNAttributeSpec,
                        DumAemTargetAttr targetAttr) {
                if (StringUtils.isBlank(targetAttr.getName())) {
                        targetAttr.setName(dumSNAttributeSpec.getName());
                }
                if (StringUtils.isNotBlank(dumSNAttributeSpec.getClassName())) {
                        if (CollectionUtils.isEmpty(targetAttr.getSourceAttrs())) {
                                setClassNameInNewSourceAttrs(dumSNAttributeSpec, targetAttr);
                        } else {
                                setClassNameInSourceAttrs(dumSNAttributeSpec, targetAttr);
                        }
                }
                return updateTargetAttrProperties(dumSNAttributeSpec, targetAttr);
        }

        private DumAemTargetAttr updateTargetAttrProperties(TurSNAttributeSpec dumSNAttributeSpec,
                        DumAemTargetAttr targetAttr) {
                targetAttr.setDescription(dumSNAttributeSpec.getDescription());
                targetAttr.setFacet(dumSNAttributeSpec.isFacet());
                targetAttr.setFacetName(dumSNAttributeSpec.getFacetName());
                targetAttr.setMandatory(dumSNAttributeSpec.isMandatory());
                targetAttr.setMultiValued(dumSNAttributeSpec.isMultiValued());
                targetAttr.setType(dumSNAttributeSpec.getType());
                return targetAttr;
        }

        private void setClassNameInSourceAttrs(TurSNAttributeSpec dumSNAttributeSpec,
                        DumAemTargetAttr targetAttr) {
                targetAttr.getSourceAttrs().stream().filter(
                                dumCmsSourceAttr -> Objects.nonNull(dumCmsSourceAttr) && StringUtils
                                                .isBlank(dumCmsSourceAttr.getClassName()))
                                .forEach(dumCmsSourceAttr -> dumCmsSourceAttr
                                                .setClassName(dumSNAttributeSpec.getClassName()));
        }

        private void setClassNameInNewSourceAttrs(TurSNAttributeSpec dumSNAttributeSpec,
                        DumAemTargetAttr targetAttr) {
                List<DumAemSourceAttr> sourceAttrs = Collections
                                .singletonList(DumAemSourceAttr.builder()
                                                .className(dumSNAttributeSpec.getClassName())
                                                .uniqueValues(false)
                                                .convertHtmlToText(false)
                                                .build());
                targetAttr.setSourceAttrs(sourceAttrs);
                targetAttr.setClassName(dumSNAttributeSpec.getClassName());
        }
}
