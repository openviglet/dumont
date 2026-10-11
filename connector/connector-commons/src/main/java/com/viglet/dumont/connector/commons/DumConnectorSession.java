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

package com.viglet.dumont.connector.commons;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public class DumConnectorSession {
    private String source;
    private String transactionId;
    private String providerName;
    private Locale locale;
    /** Told of each document deindexed for this session (D113); none by default. */
    private DumDeindexListener deindexListener;

    public DumConnectorSession(String source, String providerName, Locale locale) {
        this.source = source;
        this.transactionId = UUID.randomUUID().toString();
        this.providerName = providerName;
        this.locale = locale;
    }

    /** Tell {@code listener} of each document deindexed for this session (D113). */
    public DumConnectorSession onDeindex(DumDeindexListener listener) {
        this.deindexListener = listener;
        return this;
    }

    /** Report one deindexed document to the session's listener, if it has one (D113). */
    public void deindexed(String objectId, List<String> sites, Locale locale) {
        if (deindexListener != null) {
            deindexListener.deindexed(objectId, sites, locale);
        }
    }
}
