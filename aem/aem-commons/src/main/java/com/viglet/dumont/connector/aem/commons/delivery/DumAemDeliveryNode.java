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

package com.viglet.dumont.connector.aem.commons.delivery;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.ACTIVATE;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_LAST_MODIFIED;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_LAST_REPLICATED;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_LAST_REPLICATED_PUBLISH;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_LAST_REPLICATION_ACTION;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_LAST_REPLICATION_ACTION_PUBLISH;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.DATE_JSON_FORMAT;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.JCR_CONTENT;
import static org.apache.jackrabbit.JcrConstants.JCR_PRIMARYTYPE;
import static org.apache.jackrabbit.JcrConstants.JCR_TITLE;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

/**
 * Builds the {@code .infinity.json} node shape every delivery surface has to produce (roadmap
 * <strong>D67</strong>).
 *
 * <p>
 * Lifted out of D61's page implementation because a Content Fragment and an asset owe the same
 * translation: the primary type, a {@code jcr:content} carrying title and dates, and the
 * replication properties that mark the content delivered. Content the delivery tier serves is
 * published by definition, which is the same conclusion {@code DumAemEvent.PUBLISHING} forces on
 * the author path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.7
 */
public final class DumAemDeliveryNode {

    /** {@code DATE_JSON_FORMAT} as a {@code java.time} formatter, for the translated dates. */
    private static final DateTimeFormatter AEM_DATE = DateTimeFormatter
            .ofPattern(DATE_JSON_FORMAT, Locale.ENGLISH);

    private final JSONObject node = new JSONObject();
    private final JSONObject jcrContent = new JSONObject();

    private DumAemDeliveryNode(String primaryType) {
        node.put(JCR_PRIMARYTYPE, primaryType);
        // Served by the delivery tier, therefore published. Both properties are read.
        jcrContent.put(CQ_LAST_REPLICATION_ACTION, ACTIVATE);
        jcrContent.put(CQ_LAST_REPLICATION_ACTION_PUBLISH, ACTIVATE);
    }

    /** A node of the given JCR primary type, already stamped delivered. */
    public static DumAemDeliveryNode of(String primaryType) {
        return new DumAemDeliveryNode(primaryType);
    }

    /** Sets {@code jcr:title}, when the surface reported one. */
    public DumAemDeliveryNode title(String title) {
        return contentProperty(JCR_TITLE, title);
    }

    /**
     * Sets the modification and replication dates from epoch milliseconds. A surface that reports no
     * date leaves them unset rather than inventing one.
     */
    public DumAemDeliveryNode lastModified(Long epochMillis) {
        if (epochMillis == null) {
            return this;
        }
        String formatted = AEM_DATE.format(Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault()));
        contentProperty(CQ_LAST_MODIFIED, formatted);
        contentProperty(CQ_LAST_REPLICATED, formatted);
        return contentProperty(CQ_LAST_REPLICATED_PUBLISH, formatted);
    }

    /**
     * Sets the dates from an ISO-8601 instant, which is how both the Content Fragment and the Assets
     * delivery APIs report them. An unparseable value leaves the dates unset.
     */
    public DumAemDeliveryNode lastModified(String isoInstant) {
        return lastModified(epochMillis(isoInstant));
    }

    /** One property under {@code jcr:content}; blank values are skipped, never written empty. */
    public DumAemDeliveryNode contentProperty(String name, String value) {
        if (StringUtils.isNotBlank(value)) {
            jcrContent.put(name, value);
        }
        return this;
    }

    /** One boolean property under {@code jcr:content} — {@code contentFragment}, in practice. */
    public DumAemDeliveryNode contentFlag(String name, boolean value) {
        jcrContent.put(name, value);
        return this;
    }

    /** A nested object under {@code jcr:content}, e.g. the {@code data} folder of a fragment. */
    public DumAemDeliveryNode contentNode(String name, JSONObject value) {
        if (value != null) {
            jcrContent.put(name, value);
        }
        return this;
    }

    /** A child node on the content itself — a marker the existing traversal walks into. */
    public DumAemDeliveryNode child(String name, JSONObject value) {
        if (StringUtils.isNotBlank(name) && value != null) {
            node.put(name, value);
        }
        return this;
    }

    public JSONObject build() {
        node.put(JCR_CONTENT, jcrContent);
        return node;
    }

    /** Epoch millis for an ISO-8601 instant, or null when it is absent or unparseable. */
    public static Long epochMillis(String isoInstant) {
        if (StringUtils.isBlank(isoInstant)) {
            return null;
        }
        try {
            return Instant.parse(isoInstant.trim()).toEpochMilli();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
