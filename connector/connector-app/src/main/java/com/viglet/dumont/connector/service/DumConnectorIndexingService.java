package com.viglet.dumont.connector.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.domain.DumSNSiteLocale;
import com.viglet.dumont.connector.persistence.model.DumConnectorDependencyModel;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.persistence.repository.DumConnectorIndexingRepository;
import com.viglet.turing.client.sn.job.TurSNJobItem;

@Service
public class DumConnectorIndexingService {
        private final DumConnectorIndexingRepository dumConnectorIndexingRepository;
        private final boolean connectorDependencies;

        public DumConnectorIndexingService(
                        DumConnectorIndexingRepository dumConnectorIndexingRepository,
                        @Value("${dumont.connector.dependencies.enabled:true}") boolean connectorDependencies) {
                this.dumConnectorIndexingRepository = dumConnectorIndexingRepository;
                this.connectorDependencies = connectorDependencies;
        }

        public List<String> findByDependencies(String source, String provider,
                        List<String> referenceIds) {
                return dumConnectorIndexingRepository.findObjectIdsByDependencies(source, provider,
                                referenceIds);
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

        public void update(DumJobItemWithSession turSNJobItemWithSession,
                        DumConnectorIndexingModel indexing) {
                dumConnectorIndexingRepository.save(updateDumConnectorIndexing(indexing,
                                turSNJobItemWithSession, DumIndexingStatus.IGNORED));
        }

        public void update(DumJobItemWithSession turSNJobItemWithSession,
                        List<DumConnectorIndexingModel> dumConnectorIndexingList,
                        DumIndexingStatus status) {
                List<DumConnectorIndexingModel> managedList = dumConnectorIndexingList.stream()
                                .map(indexing -> dumConnectorIndexingRepository
                                                .findById(indexing.getId())
                                                .map(managed -> updateDumConnectorIndexing(managed,
                                                                turSNJobItemWithSession, status))
                                                .orElse(null))
                                .filter(Objects::nonNull).toList();
                dumConnectorIndexingRepository.saveAll(managedList);
        }

        public void save(DumJobItemWithSession turSNJobItemWithSession, DumIndexingStatus status) {
                dumConnectorIndexingRepository
                                .save(createDumConnectorIndexing(turSNJobItemWithSession, status));
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

        private DumConnectorIndexingModel updateDumConnectorIndexing(
                        DumConnectorIndexingModel dumConnectorIndexing,
                        DumJobItemWithSession turSNJobItemWithSession, DumIndexingStatus status) {
                dumConnectorIndexing
                                .setChecksum(turSNJobItemWithSession.turSNJobItem().getChecksum())
                                .setTransactionId(turSNJobItemWithSession.session()
                                                .getTransactionId())
                                .setModificationDate(new Date()).setStatus(status)
                                .setStandalone(turSNJobItemWithSession.standalone())
                                .setSites(turSNJobItemWithSession.turSNJobItem().getSiteNames())
                                .setDependencies(getDependencies(turSNJobItemWithSession,
                                                dumConnectorIndexing));
                return dumConnectorIndexing;
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
                return dumConnectorIndexingModel;
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

        public List<String> getObjectIdList(String source, String environment,
                        DumSNSiteLocale siteLocale, String provider) {
                return dumConnectorIndexingRepository.findAllObjectIds(source,
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
                                .stream().map(indexing -> getConnectorIndexing(indexing))
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
}
