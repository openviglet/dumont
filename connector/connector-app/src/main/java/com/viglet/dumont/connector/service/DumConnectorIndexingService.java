package com.viglet.dumont.connector.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.domain.DumConnectorMonitoringPage;
import com.viglet.dumont.connector.domain.DumConnectorMonitoringRequest;
import com.viglet.dumont.connector.domain.DumSNSiteLocale;
import com.viglet.dumont.connector.persistence.model.DumConnectorDependencyModel;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel.OperationType;
import com.viglet.dumont.connector.persistence.repository.DumConnectorIndexingRepository;
import com.viglet.dumont.connector.persistence.repository.DumConnectorIndexingStatsRepository;
import com.viglet.dumont.connector.persistence.specification.DumConnectorIndexingSpecification;
import com.viglet.turing.client.sn.job.TurSNJobItem;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DumConnectorIndexingService {
        private final DumConnectorIndexingRepository dumConnectorIndexingRepository;
        private final DumConnectorIndexingStatsRepository dumConnectorIndexingStatsRepository;
        private final boolean connectorDependencies;
        private final Map<String, PendingStats> pendingStatsMap = new ConcurrentHashMap<>();
        private final Set<String> processingSources = ConcurrentHashMap.newKeySet();

        public record PendingStats(Date startTime, OperationType operationType) {}

        public DumConnectorIndexingService(
                        DumConnectorIndexingRepository dumConnectorIndexingRepository,
                        DumConnectorIndexingStatsRepository dumConnectorIndexingStatsRepository,
                        @Value("${dumont.dependencies.enabled:true}") boolean connectorDependencies) {
                this.dumConnectorIndexingRepository = dumConnectorIndexingRepository;
                this.dumConnectorIndexingStatsRepository = dumConnectorIndexingStatsRepository;
                this.connectorDependencies = connectorDependencies;
        }

        public boolean tryStartProcessing(String source) {
                return processingSources.add(source);
        }

        public void finishProcessing(String source) {
                processingSources.remove(source);
        }

        public boolean isProcessing(String source) {
                return processingSources.contains(source);
        }

        public void trackIndexingStart(String source, String provider,
                        OperationType operationType) {
                String key = source + "|" + provider;
                pendingStatsMap.put(key, new PendingStats(new Date(), operationType));
        }

        public void completeIndexingStats(String source, String provider) {
                String key = source + "|" + provider;
                PendingStats pending = pendingStatsMap.remove(key);
                if (pending == null) {
                        log.warn("No pending stats found for key '{}'. Available keys: {}",
                                        key, pendingStatsMap.keySet());
                        return;
                }
                Date endTime = new Date();
                long documentCount = countBySourceAndProviderSince(source, provider,
                                pending.startTime());
                long durationMs = endTime.getTime() - pending.startTime().getTime();
                double documentsPerMinute = durationMs > 0
                                ? (documentCount * 60_000.0) / durationMs
                                : 0;
                List<String> environments = dumConnectorIndexingRepository
                                .findAllEnvironmentsBySourceAndProvider(source, provider);
                List<Locale> locales = dumConnectorIndexingRepository
                                .findAllLocalesBySourceAndProvider(source, provider);
                List<String> sites = dumConnectorIndexingRepository.distinctSites(source, provider);
                DumConnectorIndexingStatsModel stats = DumConnectorIndexingStatsModel.builder()
                                .provider(provider)
                                .source(source)
                                .operationType(pending.operationType())
                                .startTime(pending.startTime())
                                .endTime(endTime)
                                .documentCount(documentCount)
                                .documentsPerMinute(documentsPerMinute)
                                .environment(String.join(", ", environments))
                                .locale(locales.stream().filter(java.util.Objects::nonNull)
                                                .findFirst().orElse(null))
                                .sites(sites)
                                .build();
                dumConnectorIndexingStatsRepository.save(stats);
                log.info("Indexing stats for source '{}': {} documents in {}ms ({} docs/min)",
                                source, documentCount, durationMs,
                                String.format("%.2f", documentsPerMinute));
        }

        public List<String> findByDependencies(String source, String provider,
                        List<String> referenceIds) {
                return dumConnectorIndexingRepository.findObjectIdsByDependencies(source, provider,
                                referenceIds);
        }

        public void deindexedStatus(DumJobItemWithSession turSNJobItemWithSession) {
                createOrUpdateDumConnectorIndexing(turSNJobItemWithSession, DumIndexingStatus.DEINDEXED);
        }

        public void delete(DumJobItemWithSession turSNJobItemWithSession) {
                TurSNJobItem turSNJobItem = turSNJobItemWithSession.turSNJobItem();
                DumConnectorSession session = turSNJobItemWithSession.session();
                dumConnectorIndexingRepository.deleteByObjectIdAndSourceAndEnvironmentAndProvider(
                                turSNJobItem.getId(), session.getSource(),
                                turSNJobItem.getEnvironment(), session.getProviderName());

        }

        public void deleteByProvider(String provider) {
                dumConnectorIndexingRepository.deleteByProvider(provider);
        }

        public List<DumConnectorIndexingModel> findAllByProviderAndObjectIdIn(String provider,
                        Collection<String> objectIds) {
                return dumConnectorIndexingRepository.findAllByProviderAndObjectIdIn(provider,
                                objectIds);
        }

        public void deleteByProviderAndSourceAndObjectIdIn(String provider, String source,
                        Collection<String> objectIds) {
                dumConnectorIndexingRepository.deleteByProviderAndSourceAndObjectIdIn(provider,
                                source, objectIds);

        }

        public void deleteByProviderAndSource(String provider, String source) {
                dumConnectorIndexingRepository.deleteByProviderAndSource(provider, source);
        }

        public void deleteContentsToBeDeIndexed(DumConnectorSession session) {
                dumConnectorIndexingRepository.deleteBySourceAndProviderAndTransactionIdNot(
                                session.getSource(), session.getProviderName(),
                                session.getTransactionId());
        }

        public void update(DumJobItemWithSession turSNJobItemWithSession) {
                DumConnectorIndexingModel updated = createOrUpdateDumConnectorIndexing(
                                turSNJobItemWithSession, DumIndexingStatus.IGNORED);
                if (updated != null) {
                        dumConnectorIndexingRepository.save(updated);
                }
        }

        public void update(DumJobItemWithSession turSNJobItemWithSession,
                        List<DumConnectorIndexingModel> dumConnectorIndexingList,
                        DumIndexingStatus status) {
                if (dumConnectorIndexingList.isEmpty()) {
                        return;
                }
                List<DumConnectorIndexingModel> updatedList = dumConnectorIndexingList.stream()
                                .map(indexing -> updateDumConnectorIndexing(indexing,
                                                turSNJobItemWithSession, status))
                                .toList();
                dumConnectorIndexingRepository.saveAll(updatedList);
        }

        public void save(DumJobItemWithSession turSNJobItemWithSession, DumIndexingStatus status) {
                DumConnectorIndexingModel indexing = createOrUpdateDumConnectorIndexing(turSNJobItemWithSession,
                                status);
                if (indexing != null) {
                        dumConnectorIndexingRepository.save(indexing);
                }
        }

        public boolean exists(DumJobItemWithSession turSNJobItemWithSession) {
                TurSNJobItem turSNJobItem = turSNJobItemWithSession.turSNJobItem();
                DumConnectorSession session = turSNJobItemWithSession.session();
                return dumConnectorIndexingRepository
                                .existsByObjectIdAndSourceAndEnvironmentAndProvider(
                                                turSNJobItem.getId(), session.getSource(),
                                                turSNJobItem.getEnvironment(),
                                                session.getProviderName());
        }

        public List<DumConnectorIndexingModel> getList(
                        DumJobItemWithSession turSNJobItemWithSession) {
                TurSNJobItem turSNJobItem = turSNJobItemWithSession.turSNJobItem();
                DumConnectorSession session = turSNJobItemWithSession.session();
                return dumConnectorIndexingRepository
                                .findByObjectIdAndSourceAndEnvironmentAndProvider(
                                                turSNJobItem.getId(), session.getSource(),
                                                turSNJobItem.getEnvironment(),
                                                session.getProviderName());
        }

        public List<DumConnectorIndexingModel> getShouldBeDeIndexedList(
                        DumConnectorSession session) {
                return dumConnectorIndexingRepository
                                .findBySourceAndProviderAndTransactionIdNotAndStandalone(
                                                session.getSource(), session.getProviderName(),
                                                session.getTransactionId(), false);
        }

        public List<DumConnectorIndexingModel> findAll() {
                return dumConnectorIndexingRepository
                                .findAllByOrderByModificationDateDesc(Limit.of(50));
        }

        public List<String> getAllSources(String provider) {
                return dumConnectorIndexingRepository.findAllSources(provider);
        }

        public DumConnectorMonitoringPage search(DumConnectorMonitoringRequest request,
                        String provider) {
                Set<String> allowedSortFields = Set.of("modificationDate", "objectId", "status",
                                "environment", "locale", "created");
                String sortField = allowedSortFields.contains(request.getSortBy())
                                ? request.getSortBy()
                                : "modificationDate";
                Sort sort = "asc".equalsIgnoreCase(request.getSortDirection())
                                ? Sort.by(sortField).ascending()
                                : Sort.by(sortField).descending();
                PageRequest pageRequest = PageRequest.of(request.getPage(), request.getSize(), sort);

                Page<DumConnectorIndexingModel> page = dumConnectorIndexingRepository.findAll(
                                DumConnectorIndexingSpecification.fromRequest(request, provider),
                                pageRequest);

                List<String> sources = dumConnectorIndexingRepository.findAllSources(provider);
                List<String> environments = dumConnectorIndexingRepository
                                .findAllEnvironments(provider);
                List<String> locales = dumConnectorIndexingRepository.findAllLocales(provider)
                                .stream().filter(java.util.Objects::nonNull)
                                .map(Locale::toString).toList();
                List<String> sites = dumConnectorIndexingRepository.findAllSites(provider);

                return DumConnectorMonitoringPage.builder()
                                .sources(sources)
                                .environments(environments)
                                .locales(locales)
                                .sites(sites)
                                .content(page.getContent())
                                .page(page.getNumber())
                                .size(page.getSize())
                                .totalElements(page.getTotalElements())
                                .totalPages(page.getTotalPages())
                                .build();
        }

        private HashSet<DumConnectorDependencyModel> getDependencies(
                        DumJobItemWithSession turSNJobItemWithSession,
                        DumConnectorIndexingModel dumConnectorIndexingModel) {
                if (!connectorDependencies) {
                        return new HashSet<>();
                }
                return new HashSet<>(turSNJobItemWithSession.dependencies().stream()
                                .map(dep -> DumConnectorDependencyModel.builder().objectId(dep)
                                                .reference(dumConnectorIndexingModel).build())
                                .toList());
        }

        private DumConnectorIndexingModel createOrUpdateDumConnectorIndexing(
                        DumJobItemWithSession turSNJobItemWithSession, DumIndexingStatus status) {
                TurSNJobItem turSNJobItem = turSNJobItemWithSession.turSNJobItem();
                DumConnectorSession session = turSNJobItemWithSession.session();
                return dumConnectorIndexingRepository
                                .findByObjectIdAndSourceAndEnvironmentAndProvider(
                                                turSNJobItem.getId(), session.getSource(),
                                                turSNJobItem.getEnvironment(),
                                                session.getProviderName())
                                .stream().findFirst()
                                .map(existing -> updateDumConnectorIndexing(existing,
                                                turSNJobItemWithSession, status))
                                .orElseGet(() -> createDumConnectorIndexing(turSNJobItemWithSession,
                                                status));

        }

        private DumConnectorIndexingModel updateDumConnectorIndexing(
                        DumConnectorIndexingModel dumConnectorIndexing,
                        DumJobItemWithSession turSNJobItemWithSession, DumIndexingStatus status) {
                dumConnectorIndexing
                                .setChecksum(turSNJobItemWithSession.turSNJobItem().getChecksum())
                                .setTransactionId(turSNJobItemWithSession.session()
                                                .getTransactionId())
                                .setModificationDate(new Date()).setStatus(status)
                                .setStandalone(turSNJobItemWithSession.standalone());
                dumConnectorIndexing.setSites(turSNJobItemWithSession.turSNJobItem().getSiteNames());
                dumConnectorIndexing.setDependencies(getDependencies(turSNJobItemWithSession,
                                dumConnectorIndexing));
                return dumConnectorIndexingRepository.save(dumConnectorIndexing);
        }

        private DumConnectorIndexingModel createDumConnectorIndexing(
                        DumJobItemWithSession turSNJobItemWithSession, DumIndexingStatus status) {
                TurSNJobItem turSNJobItem = turSNJobItemWithSession.turSNJobItem();
                DumConnectorSession dumConnectorSession = turSNJobItemWithSession.session();
                DumConnectorIndexingModel dumConnectorIndexingModel = DumConnectorIndexingModel
                                .builder().objectId(turSNJobItem.getId())
                                .source(dumConnectorSession.getSource())
                                .transactionId(dumConnectorSession.getTransactionId())
                                .locale(turSNJobItem.getLocale())
                                .checksum(turSNJobItem.getChecksum()).created(new Date())
                                .modificationDate(new Date()).sites(turSNJobItem.getSiteNames())
                                .environment(turSNJobItem.getEnvironment()).status(status)
                                .standalone(turSNJobItemWithSession.standalone())
                                .provider(dumConnectorSession.getProviderName()).build();
                dumConnectorIndexingModel.setDependencies(getDependencies(turSNJobItemWithSession,
                                dumConnectorIndexingModel));
                return dumConnectorIndexingRepository.save(dumConnectorIndexingModel);

        }

        public boolean isChecksumDifferent(DumJobItemWithSession turSNJobItemWithSession) {
                TurSNJobItem turSNJobItem = turSNJobItemWithSession.turSNJobItem();
                DumConnectorSession session = turSNJobItemWithSession.session();
                return dumConnectorIndexingRepository
                                .existsByObjectIdAndSourceAndEnvironmentAndChecksumNot(
                                                turSNJobItem.getId(), session.getSource(),
                                                turSNJobItem.getEnvironment(),
                                                turSNJobItem.getChecksum());
        }

        public List<DumConnectorIndexingModel> getBySourceAndProvider(String source,
                        String provider) {
                return dumConnectorIndexingRepository
                                .findAllBySourceAndProviderOrderByModificationDateDesc(source,
                                                provider, Limit.of(50));
        }

        public List<String> getSites(String source, String provider) {
                return dumConnectorIndexingRepository.distinctSites(source, provider);
        }

        public List<String> getEnvironment(String site, String provider) {
                return dumConnectorIndexingRepository.distinctEnvironment(site, provider);
        }

        public List<String> listIndexedObjectIds(String source, String environment,
                        DumSNSiteLocale siteLocale, String provider) {
                return dumConnectorIndexingRepository.findAllIndexedObjectIds(source,
                                siteLocale.getLanguage(), environment, provider);
        }

        public Collection<String> validateObjectIdList(String source, String environment,
                        DumSNSiteLocale siteLocale, String provider, List<String> objectIdList) {
                return dumConnectorIndexingRepository.distinctObjectId(source,
                                siteLocale.getLanguage(), environment, provider, objectIdList);
        }

        public List<DumConnectorIndexing> getIndexingItem(String objectId, String source,
                        String provider) {
                List<DumConnectorIndexing> dtoList = new ArrayList<>();
                dumConnectorIndexingRepository
                                .findByObjectIdAndSourceAndProvider(objectId, source, provider)
                                .stream().map(this::getConnectorIndexing)
                                .forEach(dtoList::add);
                return dtoList;
        }

        public DumConnectorIndexing getConnectorIndexing(DumConnectorIndexingModel indexing) {
                return DumConnectorIndexing.builder().checksum(indexing.getChecksum())
                                .created(indexing.getCreated())
                                .environment(indexing.getEnvironment()).id(indexing.getId())
                                .locale(indexing.getLocale())
                                .modificationDate(indexing.getModificationDate())
                                .source(indexing.getSource()).objectId(indexing.getObjectId())
                                .sites(indexing.getSites()).status(indexing.getStatus())
                                .transactionId(indexing.getTransactionId()).build();
        }

        public List<DumConnectorIndexingModel> getUnprocessedBySourceAndProvider(String source,
                        String provider) {
                return dumConnectorIndexingRepository
                                .findAllBySourceAndProviderAndStatusOrderByModificationDateDesc(
                                                source, provider, DumIndexingStatus.NOT_PROCESSED,
                                                Limit.of(500));
        }

        public boolean existsByObjectIdAndSourceAndProvider(String objectId, String source,
                        String provider) {
                return dumConnectorIndexingRepository.existsByObjectIdAndSourceAndProvider(
                                objectId, source, provider);
        }

        public DumConnectorIndexingModel createUnprocessedRecord(String objectId, String source,
                        String provider) {
                return createUnprocessedRecord(objectId, source, provider, null);
        }

        public DumConnectorIndexingModel createUnprocessedRecord(String objectId, String source,
                        String provider, Locale locale) {
                return createUnprocessedRecord(objectId, source, provider, locale, null, null);
        }

        public DumConnectorIndexingModel createUnprocessedRecord(String objectId, String source,
                        String provider, Locale locale, String environment,
                        List<String> sites) {
                DumConnectorIndexingModel model = DumConnectorIndexingModel.builder()
                                .objectId(objectId)
                                .source(source)
                                .provider(provider)
                                .locale(locale)
                                .environment(environment)
                                .sites(sites)
                                .status(DumIndexingStatus.NOT_PROCESSED)
                                .created(new Date())
                                .modificationDate(new Date())
                                .build();
                return dumConnectorIndexingRepository.save(model);
        }

        public boolean existsByObjectIdAndSourceAndEnvironmentAndProvider(String objectId,
                        String source, String environment, String provider) {
                return dumConnectorIndexingRepository.existsByObjectIdAndSourceAndEnvironmentAndProvider(
                                objectId, source, environment, provider);
        }

        public List<DumConnectorIndexingModel> findUnprocessedByObjectIdAndSourceAndProvider(
                        String objectId, String source, String provider) {
                return dumConnectorIndexingRepository.findByObjectIdAndSourceAndProviderAndStatus(
                                objectId, source, provider, DumIndexingStatus.NOT_PROCESSED);
        }

        public void deleteAll(List<DumConnectorIndexingModel> records) {
                dumConnectorIndexingRepository.deleteAll(records);
        }

        public long countBySourceAndProviderSince(String source, String provider, Date since) {
                return dumConnectorIndexingRepository
                                .countBySourceAndProviderAndModificationDateGreaterThanEqual(
                                                source, provider, since);
        }

        public DumConnectorIndexingStatsModel saveStats(
                        DumConnectorIndexingStatsModel stats) {
                return dumConnectorIndexingStatsRepository.save(stats);
        }

        public List<DumConnectorIndexingStatsModel> getStatsBySourceAndProvider(
                        String source, String provider) {
                return dumConnectorIndexingStatsRepository
                                .findAllBySourceAndProviderOrderByStartTimeDesc(source, provider,
                                                Limit.of(50));
        }

        public List<DumConnectorIndexingStatsModel> getAllStats() {
                return dumConnectorIndexingStatsRepository
                                .findAllByOrderByStartTimeDesc(Limit.of(50));
        }

        public long countByProvider(String provider) {
                return dumConnectorIndexingRepository.countByProvider(provider);
        }

        public Map<String, Long> countByProviderGroupBySource(String provider) {
                return dumConnectorIndexingRepository.countByProviderGroupBySource(provider).stream()
                                .collect(Collectors.toMap(
                                                row -> (String) row[0],
                                                row -> (Long) row[1]));
        }
}
