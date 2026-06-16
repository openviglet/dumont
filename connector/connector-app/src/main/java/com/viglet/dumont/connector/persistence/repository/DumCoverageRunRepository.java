/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
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

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.dumont.connector.persistence.model.DumCoverageRunModel;

public interface DumCoverageRunRepository extends JpaRepository<DumCoverageRunModel, Integer> {

    /** A source's coverage runs, newest first (id breaks ties for runs captured in the same instant). */
    List<DumCoverageRunModel> findAllBySourceOrderByCapturedAtDescIdDesc(String source, Limit limit);
}
