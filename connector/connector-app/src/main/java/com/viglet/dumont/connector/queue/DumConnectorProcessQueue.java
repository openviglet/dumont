/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.queue;

import static com.viglet.dumont.connector.commons.logging.DumConnectorLoggingUtils.setSuccessStatus;
import static com.viglet.dumont.connector.constant.DumConnectorConstants.CONNECTOR_INDEXING_QUEUE;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.connector.commons.ground.DumGrounding;
import com.viglet.dumont.connector.commons.logging.DumJobTrail;
import com.viglet.dumont.connector.commons.plugin.DumIndexingDeliveryException;
import com.viglet.dumont.connector.commons.plugin.DumIndexingPlugin;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DumConnectorProcessQueue {
    private final DumIndexingPlugin indexingPlugin;
    private final DumConnectorIndexingService indexingService;

    public DumConnectorProcessQueue(DumIndexingPlugin indexingPlugin,
            DumConnectorIndexingService indexingService) {
        this.indexingPlugin = indexingPlugin;
        this.indexingService = indexingService;
        log.info("DumConnectorProcessQueue initialized with indexing provider: {}",
                indexingPlugin.getProviderName());
    }

    @JmsListener(destination = CONNECTOR_INDEXING_QUEUE)
    @Transactional
    public void receiveAndSendToDumont(TurSNJobItems turSNJobItems) {
        List<String> sites = new ArrayList<>();
        List<Locale> locales = new ArrayList<>();
        // D138: what the batch publishes, per source, so a refused delivery can be resent.
        Map<String, List<String>> createdBySource = new LinkedHashMap<>();
        if (turSNJobItems == null || turSNJobItems.getTuringDocuments().isEmpty()) {
            log.info("Job is empty, no action.");
            return;
        }
        log.info("Processing job from queue with provider: {}", indexingPlugin.getProviderName());
        for (TurSNJobItem turSNJobItem : turSNJobItems) {
            // D71: read the trail before it is stripped. The session does not cross the queue, so
            // this is the only way this row can name the same source the SENT_TO_QUEUE row did.
            String source = DumJobTrail.source(turSNJobItem);
            String transactionId = DumJobTrail.transactionId(turSNJobItem);
            if (source != null && turSNJobItem.getTurSNJobAction() == TurSNJobAction.CREATE
                    && turSNJobItem.getId() != null) {
                createdBySource.computeIfAbsent(source, s -> new ArrayList<>()).add(turSNJobItem.getId());
            }
            // Grounding contract, enforced end-to-end (roadmap D03): no matter how a connector
            // assembled its attributes, an absent field must not reach the backend as an empty
            // value and pollute facets. The structured SPI already grounds at record-build time;
            // this is the single boundary that also covers the loose-map connectors.
            //
            // The trail comes off in the same replacement (D71) — it is connector bookkeeping, and
            // anything still on the item here becomes a field on the indexed document.
            turSNJobItem.setAttributes(
                    DumGrounding.groundAttributes(DumJobTrail.strip(turSNJobItem.getAttributes())));
            if (!locales.contains(turSNJobItem.getLocale())) {
                locales.add(turSNJobItem.getLocale());
            }
            turSNJobItem.getSiteNames().forEach(site -> {
                if (!sites.contains(site)) {
                    sites.add(site);
                }
            });
            log.debug("Processing {} job item", turSNJobItem.getId());
            setSuccessStatus(turSNJobItem, source, transactionId,
                    DumIndexingStatus.RECEIVED_AND_SENT_TO_TURING, null);
        }
        if (locales.isEmpty()) {
            turSNJobItems.add(new TurSNJobItem(TurSNJobAction.COMMIT, sites));
        } else {
            locales.forEach(locale -> turSNJobItems.add(new TurSNJobItem(TurSNJobAction.COMMIT, sites, locale)));
        }
        try {
            indexingPlugin.index(turSNJobItems);
        } catch (DumIndexingDeliveryException e) {
            // Not rethrown: the rollback would undo the very marks that make the next run resend.
            createdBySource.forEach((source, ids) -> {
                int marked = indexingService.recordDeliveryFailure(source, ids, e.getMessage());
                log.error("Batch for source '{}' was not delivered to {} ({}): {} document(s) marked "
                        + "for resending", source, indexingPlugin.getProviderName(), e.getMessage(), marked);
            });
        }
    }
}
