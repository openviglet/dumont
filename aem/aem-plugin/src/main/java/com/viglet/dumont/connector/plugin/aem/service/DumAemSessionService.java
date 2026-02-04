package com.viglet.dumont.connector.plugin.aem.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemContentMapping;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.plugin.aem.api.DumAemPathList;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;

@Service
public class DumAemSessionService {
        private final DumAemSourceService dumAemSourceService;
        private final DumAemContentDefinitionService dumAemContentDefinitionService;
        private final DumAemContentMappingService dumAemContentMappingService;

        public DumAemSessionService(DumAemSourceService dumAemSourceService,
                        DumAemContentDefinitionService dumAemContentDefinitionService,
                        DumAemContentMappingService dumAemContentMappingService) {
                this.dumAemSourceService = dumAemSourceService;
                this.dumAemContentDefinitionService = dumAemContentDefinitionService;
                this.dumAemContentMappingService = dumAemContentMappingService;
        }

        public DumAemSession getDumAemSession(DumAemSource dumAemSource,
                        DumAemPathList dumAemPathList, boolean standalone) {
                // Retrieve content mapping once and reuse
                DumAemContentMapping dumAemContentMapping = dumAemContentMappingService
                                .getDumAemContentMapping(dumAemSource);

                // Get attribute specifications
                List<TurSNAttributeSpec> attributeSpecs = dumAemContentDefinitionService
                                .getAttributeSpec(dumAemContentMapping);

                // Get connector session and configuration
                DumConnectorSession session = dumAemSourceService.getDumConnectorSession(dumAemSource);
                DumAemConfiguration dumAemConfiguration = dumAemSourceService.getDumAemConfiguration(dumAemSource);

                // Get optional model
                DumAemModel model = dumAemContentDefinitionService
                                .getModel(dumAemConfiguration, dumAemSource).orElse(null);

                // Extract event and recursion settings with null-safe operations
                DumAemEvent event = Optional.ofNullable(dumAemPathList)
                                .map(DumAemPathList::getEvent)
                                .orElse(DumAemEvent.NONE);

                boolean recursive = Optional.ofNullable(dumAemPathList)
                                .map(DumAemPathList::getRecursive)
                                .orElse(true);

                // Build and return session
                return DumAemSession.builder()
                                .configuration(dumAemConfiguration)
                                .event(event)
                                .standalone(standalone)
                                .recursive(recursive)
                                .source(session.getSource())
                                .transactionId(session.getTransactionId())
                                .sites(session.getSites())
                                .providerName(session.getProviderName())
                                .locale(session.getLocale())
                                .attributeSpecs(attributeSpecs)
                                .contentMapping(dumAemContentMapping)
                                .model(model)
                                .build();
        }

        public DumAemSession getDumAemSession(DumAemSource dumAemSource, boolean standalone) {
                return getDumAemSession(dumAemSource, (DumAemPathList) null, standalone);
        }
}
