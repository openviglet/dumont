package com.viglet.dumont.connector.plugin.aem.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.aem.commons.mappers.DumAemContentMapping;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemAttributeSpecification;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemPluginModel;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemTargetAttribute;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemAttributeSpecificationRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemPluginModelRepository;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;

@Component
public class DumAemContentMappingService {
        private final DumAemPluginModelRepository dumAemPluginModelRepository;
        private final DumAemAttributeSpecificationRepository dumAemAttributeSpecificationRepository;

        public DumAemContentMappingService(DumAemPluginModelRepository dumAemPluginModelRepository,
                        DumAemAttributeSpecificationRepository dumAemAttributeSpecificationRepository) {
                this.dumAemPluginModelRepository = dumAemPluginModelRepository;
                this.dumAemAttributeSpecificationRepository = dumAemAttributeSpecificationRepository;
        }

        public @NotNull DumAemContentMapping getDumAemContentMapping(DumAemSource dumAemSource) {
                return DumAemContentMapping.builder()
                                .deltaClassName(dumAemSource.getDeltaClass())
                                .models(getDumAemModels(dumAemSource))
                                .targetAttrDefinitions(
                                                collectTurSNAttributeSpecifications(dumAemSource))
                                .build();
        }

        private @NotNull List<DumAemModel> getDumAemModels(DumAemSource dumAemSource) {
                return dumAemPluginModelRepository.findByDumAemSource(dumAemSource).stream()
                                .map(pluginModel -> DumAemModel.builder()
                                                .className(pluginModel.getClassName())
                                                .type(pluginModel.getType())
                                                .targetAttrs(getDumAemTargetAttrs(pluginModel))
                                                .build())
                                .toList();
        }

        private static @NotNull List<DumAemTargetAttr> getDumAemTargetAttrs(
                        DumAemPluginModel pluginModel) {
                return pluginModel.getTargetAttrs().stream()
                                .map(targetAttr -> DumAemTargetAttr.builder()
                                                .name(targetAttr.getName())
                                                .sourceAttrs(getDumAemSourceAttrs(targetAttr))
                                                .build())
                                .collect(Collectors.toList());
        }

        private static @NotNull List<DumAemSourceAttr> getDumAemSourceAttrs(
                        DumAemTargetAttribute targetAttr) {
                return targetAttr.getSourceAttrs().stream()
                                .map(sourceAttr -> DumAemSourceAttr.builder()
                                                .className(sourceAttr.getClassName())
                                                .name(sourceAttr.getName()).convertHtmlToText(false)
                                                .uniqueValues(false).build())
                                .toList();
        }

        private @NotNull List<TurSNAttributeSpec> collectTurSNAttributeSpecifications(
                        DumAemSource dumAemSource) {
                return dumAemAttributeSpecificationRepository.findByDumAemSource(dumAemSource)
                                .map(attributeSpecifications -> attributeSpecifications.stream()
                                                .map(this::mapToTurSNAttributeSpec)
                                                .collect(Collectors.toList()))
                                .orElse(new ArrayList<>());
        }

        private TurSNAttributeSpec mapToTurSNAttributeSpec(
                        DumAemAttributeSpecification attributeSpec) {
                return TurSNAttributeSpec.builder()
                                .className(attributeSpec.getClassName())
                                .name(attributeSpec.getName())
                                .type(attributeSpec.getType())
                                .facetName(attributeSpec.getFacetNames())
                                .description(attributeSpec.getDescription())
                                .mandatory(attributeSpec.isMandatory())
                                .multiValued(attributeSpec.isMultiValued())
                                .facet(attributeSpec.isFacet())
                                .build();
        }
}
