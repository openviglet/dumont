/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;

public interface DumConnectorIndexingRepository
                extends JpaRepository<DumConnectorIndexingModel, Integer> {
        List<DumConnectorIndexingModel> findByDependenciesReferenceId(int referenceId);

        boolean existsByObjectIdAndSourceAndEnvironmentAndProvider(String objectId, String source,
                        String environment, String provider);

        boolean existsByObjectIdAndSourceAndEnvironmentAndChecksumNot(String objectId,
                        String source, String environment, String checksum);

        List<DumConnectorIndexingModel> findBySourceAndProviderAndTransactionIdNotAndStandalone(
                        String source, String provider, String transactionId, boolean standalone);

        List<DumConnectorIndexingModel> findByObjectIdAndSourceAndEnvironmentAndProvider(
                        String objectId, String source, String environment, String provider);

        List<DumConnectorIndexingModel> findByObjectIdAndSourceAndProvider(String objectId,
                        String source, String provider);

        List<DumConnectorIndexingModel> findAllBySourceAndProviderOrderByModificationDateDesc(
                        String source, String provider, Limit limit);

        List<DumConnectorIndexingModel> findAllByOrderByModificationDateDesc(Limit limit);

        List<DumConnectorIndexingModel> findAllByProviderAndObjectIdIn(String provider,
                        Collection<String> objectIds);

        @Transactional
        void deleteByObjectIdAndSourceAndEnvironmentAndProvider(String objectId, String source,
                        String environment, String provider);

        @Transactional
        void deleteByProvider(String provider);

        @Transactional
        void deleteByProviderAndSource(String provider, String source);

        @Transactional
        void deleteByProviderAndSourceAndObjectIdIn(String provider, String source,
                        Collection<String> contentIds);

        @Transactional
        void deleteBySourceAndProviderAndTransactionIdNot(String source, String provider,
                        String transactionId);

        @Query("SELECT DISTINCT i.source FROM DumConnectorIndexingModel i WHERE i.provider = :provider")
        List<String> findAllSources(@Param("provider") String provider);

        @Query("SELECT DISTINCT i.objectId FROM DumConnectorIndexingModel i WHERE i.source = :source AND "
                        + "i.locale = :locale AND i.environment IN :environment AND i.provider = :provider")
        List<String> findAllObjectIds(@Param("source") String source,
                        @Param("locale") Locale locale, @Param("environment") String environment,
                        @Param("provider") String provider);

        @Query("SELECT DISTINCT i.sites FROM DumConnectorIndexingModel i WHERE i.source = :source AND "
                        + "i.provider = :provider")
        List<String> distinctSites(@Param("source") String source,
                        @Param("provider") String provider);

        @Query("SELECT DISTINCT i.environment FROM DumConnectorIndexingModel i WHERE :site MEMBER OF i.sites AND "
                        + "i.provider = :provider")
        List<String> distinctEnvironment(@Param("site") String site,
                        @Param("provider") String provider);

        @Query("SELECT DISTINCT i.objectId FROM DumConnectorIndexingModel i WHERE i.source = :source AND i.locale = :locale AND "
                        + "i.environment IN :environment AND i.provider = :provider AND i.objectId IN :ids")
        List<String> distinctObjectId(@Param("source") String source,
                        @Param("locale") Locale locale, @Param("environment") String environment,
                        @Param("provider") String provider, @Param("ids") List<String> ids);

        @Query("SELECT DISTINCT i.objectId FROM DumConnectorIndexingModel i JOIN i.dependencies d WHERE i.source = :source AND "
                        + "i.provider = :provider AND d.objectId IN :ids")
        List<String> findObjectIdsByDependencies(@Param("source") String source,
                        @Param("provider") String provider, @Param("ids") List<String> ids);
}
