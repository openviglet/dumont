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

package com.viglet.dumont.connector.plugin.eds.persistence.model;

import java.io.Serial;
import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Lightweight key/value table used to mark plugin lifecycle state. Today the
 * only key is {@code FIRST_TIME}, written by {@link
 * com.viglet.dumont.connector.plugin.eds.onstartup.DumEDSOnStartupJpa} after the
 * initial import pass so it doesn't re-import on every restart.
 */
@Setter
@Getter
@Entity
@Table(name = "eds_config")
public class DumEDSConfigVar implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(unique = true, nullable = false, length = 25)
    private String id;

    @Column
    private String path;

    @Column
    private String value;
}
