/*
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

package com.viglet.dumont.connector.plugin.eds.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSCrawlState;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;

public interface DumEDSCrawlStateRepository extends JpaRepository<DumEDSCrawlState, String> {

    List<DumEDSCrawlState> findByDumEDSSource(DumEDSSource source);

    Optional<DumEDSCrawlState> findByDumEDSSourceAndPath(DumEDSSource source, String path);

    @Modifying
    @Query("delete from DumEDSCrawlState s where s.dumEDSSource = :source and s.path = :path")
    int deleteByDumEDSSourceAndPath(@Param("source") DumEDSSource source,
            @Param("path") String path);

    @Modifying
    @Query("delete from DumEDSCrawlState s where s.dumEDSSource = :source")
    int deleteByDumEDSSource(@Param("source") DumEDSSource source);
}
