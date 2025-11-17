package com.viglet.dumont.connector.plugin.aem.service;

import static com.viglet.dumont.commons.indexing.DumIndexingStatus.DEINDEXED;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.DATA_MASTER;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.METADATA;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.SITE;
import static com.viglet.dumont.connector.aem.commons.bean.DumAemEnv.PUBLISHING;
import static com.viglet.dumont.connector.commons.logging.DumConnectorLoggingUtils.setSuccessStatus;
import static com.viglet.turing.client.sn.TurSNConstants.ID_ATTR;
import static com.viglet.turing.client.sn.TurSNConstants.SOURCE_APPS_ATTR;
import static com.viglet.turing.client.sn.job.TurSNJobAction.CREATE;
import static com.viglet.turing.client.sn.job.TurSNJobAction.DELETE;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import com.viglet.dumont.connector.aem.commons.DumAemCommonsUtils;
import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.bean.DumAemTargetAttrValueMap;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.plugin.aem.DumAemPluginUtils;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.client.sn.job.TurSNJobItem;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DumAemJobService {
        private final DumAemService dumAemService;
        private final DumConnectorContext dumConnectorContext;
        private final DumAemContentDefinitionService dumAemContentDefinitionService;

        public DumAemJobService(DumAemContentMappingService dumAemContentMappingService,
                        DumAemService dumAemService,
                        DumConnectorContext dumConnectorContext,
                        DumAemContentDefinitionService dumAemContentDefinitionService) {
                this.dumAemService = dumAemService;
                this.dumConnectorContext = dumConnectorContext;
                this.dumAemContentDefinitionService = dumAemContentDefinitionService;
        }

        public TurSNJobItem deIndexJob(DumAemSession dumAemSession, List<String> sites,
                        Locale locale, String objectId, String environment) {
                TurSNJobItem turSNJobItem = new TurSNJobItem(DELETE, sites, locale, Map.of(ID_ATTR,
                                objectId, SOURCE_APPS_ATTR, dumAemSession.getProviderName()));
                turSNJobItem.setEnvironment(environment);
                setSuccessStatus(turSNJobItem, dumAemSession, DEINDEXED);
                return turSNJobItem;
        }

        public TurSNJobItem deIndexJob(DumAemSession dumAemSession,
                        DumConnectorIndexing dumConnectorIndexingDTO) {
                return deIndexJob(dumAemSession, dumConnectorIndexingDTO.getSites(),
                                dumConnectorIndexingDTO.getLocale(),
                                dumConnectorIndexingDTO.getObjectId(),
                                dumConnectorIndexingDTO.getEnvironment());
        }

        public @NotNull TurSNJobItem getTurSNJobItem(DumAemSession dumAemSession,
                        DumAemObject aemObject, Locale locale,
                        Map<String, Object> attributes) {
                TurSNJobItem jobItem = new TurSNJobItem(CREATE,
                                dumAemSession.getSites().stream().toList(), locale, attributes,
                                DumAemCommonsUtils.castSpecToJobSpec(
                                                DumAemCommonsUtils.getDefinitionFromModel(
                                                                dumAemSession.getAttributeSpecs(),
                                                                attributes)));
                jobItem.setChecksum(String.valueOf(dumAemContentDefinitionService
                                .getDeltaDate(aemObject, dumAemSession.getConfiguration(),
                                                dumAemSession.getContentMapping())
                                .getTime()));
                jobItem.setEnvironment(
                                aemObject.getEnvironment().toString());
                return jobItem;
        }

        public void indexObject(DumAemSession dumAemSession, DumAemObjectGeneric aemObjectGeneric) {
                indexingAuthor(dumAemSession, aemObjectGeneric);
                indexingPublish(dumAemSession, aemObjectGeneric);
        }

        public void indexingAuthor(DumAemSession dumAemSession,
                        DumAemObjectGeneric aemObjectGeneric) {
                if (dumAemSession.getConfiguration().isAuthor()) {
                        DumAemObject aemObject = new DumAemObject(aemObjectGeneric, DumAemEnv.AUTHOR);
                        indexByEnvironment(dumAemSession, aemObject);
                }
        }

        public void indexingPublish(DumAemSession dumAemSession,
                        DumAemObjectGeneric aemObjectGeneric) {
                if (dumAemSession.getConfiguration().isPublish()) {
                        DumAemObject aemObject = new DumAemObject(aemObjectGeneric, DumAemEnv.PUBLISHING);
                        if (aemObjectGeneric.isDelivered()) {
                                indexByEnvironment(dumAemSession, aemObject);
                        } else if (dumAemSession.isStandalone()) {
                                forcingDeIndex(dumAemSession, aemObject);
                        } else {
                                ignoringDeIndexLog(dumAemSession, aemObject);
                        }
                }
        }

        private void ignoringDeIndexLog(DumAemSession dumAemSession,
                        DumAemObject aemObject) {
                log.info("Ignoring deIndex because {} is not publishing.",
                                DumAemPluginUtils.getObjectDetailForLogs(dumAemSession, aemObject));
        }

        private void forcingDeIndex(DumAemSession dumAemSession, DumAemObject aemObject) {
                TurSNJobItem deIndexJobItem = deIndexJob(dumAemSession,
                                List.of(dumAemSession.getConfiguration().getPublishSNSite()),
                                DumAemCommonsUtils.getLocaleFromAemObject(
                                                dumAemSession.getConfiguration(), aemObject),
                                aemObject.getPath(), PUBLISHING.toString());
                DumJobItemWithSession dumJobItemWithSession = new DumJobItemWithSession(
                                deIndexJobItem, dumAemSession, aemObject.getDependencies(), true);
                dumConnectorContext.addJobItem(dumJobItemWithSession);
                log.info("Forcing deIndex because {} is not publishing.",
                                DumAemPluginUtils.getObjectDetailForLogs(dumAemSession, aemObject));
        }

        private void indexByEnvironment(DumAemSession dumAemSession,
                        @NotNull DumAemObject aemObject) {
                dumAemSession.setSites(Collections.singletonList(aemObject.getSNSite(
                                dumAemSession.getConfiguration())));
                createIndexJobAndSendToConnectorQueue(dumAemSession, aemObject,
                                DumAemCommonsUtils.getLocaleFromAemObject(
                                                dumAemSession.getConfiguration(),
                                                aemObject));
        }

        private void createIndexJobAndSendToConnectorQueue(DumAemSession dumAemSession,
                        DumAemObject aemObject, Locale locale) {
                TurSNJobItem turSNJobItem = getTurSNJobItem(dumAemSession, aemObject, locale,
                                getJobItemAttributes(dumAemSession,
                                                dumAemService.getTargetAttrValueMap(dumAemSession,
                                                                aemObject)));
                DumJobItemWithSession jobItemWithSession = new DumJobItemWithSession(turSNJobItem,
                                dumAemSession, aemObject.getDependencies(),
                                dumAemSession.isStandalone());
                dumConnectorContext.addJobItem(jobItemWithSession);
        }

        private static @NotNull Map<String, Object> getJobItemAttributes(
                        DumAemSession dumAemSession, DumAemTargetAttrValueMap targetAttrValueMap) {
                Map<String, Object> attributes = new HashMap<>();
                String siteName = dumAemSession.getConfiguration().getSiteName();
                if (StringUtils.isNotBlank(siteName)) {
                        attributes.put(SITE, siteName);
                }
                targetAttrValueMap.entrySet().stream()
                                .filter(e -> CollectionUtils.isNotEmpty(e.getValue()))
                                .forEach(e -> getJobItemAttribute(e, attributes));
                return attributes;
        }

        private static void getJobItemAttribute(Map.Entry<String, TurMultiValue> entry,
                        Map<String, Object> attributes) {
                String attributeName = entry.getKey();
                entry.getValue().stream().filter(StringUtils::isNotBlank)
                                .forEach(attributeValue -> {
                                        if (attributes.containsKey(attributeName)) {
                                                DumAemCommonsUtils.addItemInExistingAttribute(
                                                                attributeValue, attributes,
                                                                attributeName);
                                        } else {
                                                DumAemCommonsUtils.addFirstItemToAttribute(
                                                                attributeName, attributeValue,
                                                                attributes);
                                        }
                                });
        }

        public void createDeIndexJobAndSendToConnectorQueue(DumAemSession dumAemSession,
                        String contentId) {
                List<DumConnectorIndexing> indexingItems = dumConnectorContext
                                .getIndexingItem(contentId, dumAemSession.getSource(),
                                                dumAemSession.getProviderName());

                if (CollectionUtils.isEmpty(indexingItems)) {
                        log.debug("No indexing items found for contentId: {} in source: {}",
                                        contentId, dumAemSession.getProviderName());
                        return;
                }

                indexingItems.forEach(indexing -> {
                        try {
                                log.info("DeIndex initiated for {} - infinity Json file not found.",
                                                DumAemPluginUtils.getObjectDetailForLogs(contentId,
                                                                indexing,
                                                                dumAemSession));

                                TurSNJobItem deIndexJobItem = deIndexJob(dumAemSession, indexing);
                                DumJobItemWithSession dumJobItemWithSession = new DumJobItemWithSession(
                                                deIndexJobItem, dumAemSession,
                                                Collections.emptySet(),
                                                dumAemSession.isStandalone());
                                dumConnectorContext.addJobItem(dumJobItemWithSession);
                                log.debug("DeIndex job successfully queued for contentId: {}",
                                                contentId);

                        } catch (Exception e) {
                                log.error("Failed to create deIndex job for contentId: {} in session: {}. Error: {}",
                                                contentId, dumAemSession.getProviderName(),
                                                e.getMessage(), e);
                        }
                });
        }

        public void prepareIndexObject(DumAemSession dumAemSession,
                        DumAemObjectGeneric aemObjectGeneric) {
                if (!isObjectEligibleForIndexing(dumAemSession, aemObjectGeneric)) {
                        return;
                }
                configureObjectDataPath(dumAemSession, aemObjectGeneric);
                indexObject(dumAemSession, aemObjectGeneric);

        }

        private boolean isObjectEligibleForIndexing(DumAemSession dumAemSession,
                        DumAemObjectGeneric aemObject) {
                DumAemConfiguration config = dumAemSession.getConfiguration();
                if (!isWithinRootPath(aemObject, config)) {
                        return false;
                }
                String contentType = config.getContentType();
                if (contentType == null) {
                        log.warn("Content type is null for session {}",
                                        dumAemSession.getProviderName());
                        return false;
                }
                return !dumAemService.isNotValidType(dumAemSession.getModel(), aemObject,
                                contentType);
        }

        private boolean isWithinRootPath(DumAemObjectGeneric aemObject,
                        DumAemConfiguration config) {
                String rootPath = config.getRootPath();
                if (rootPath != null && !aemObject.getPath().startsWith(rootPath)) {
                        log.debug("Skipping object {} as it is outside the root path {}",
                                        aemObject.getPath(), rootPath);
                        return false;
                }
                return true;
        }

        private void configureObjectDataPath(DumAemSession dumAemSession,
                        DumAemObjectGeneric aemObject) {
                String contentType = dumAemSession.getConfiguration().getContentType();
                if (dumAemService.isContentFragment(dumAemSession.getModel(), contentType,
                                aemObject)) {
                        aemObject.setDataPath(DATA_MASTER);
                } else if (dumAemService.isStaticFile(dumAemSession.getModel(), contentType)) {
                        aemObject.setDataPath(METADATA);
                }
        }
}
