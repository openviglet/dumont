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

package com.viglet.dumont.connector.indexing;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.domain.DumIndexInventoryEntry;
import com.viglet.dumont.connector.commons.domain.DumIndexingSchema;
import com.viglet.dumont.connector.commons.plugin.DumIndexingDeliveryException;
import com.viglet.dumont.connector.commons.plugin.DumIndexingPlugin;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.inventory.TurSNInventoryClient;
import com.viglet.turing.client.sn.inventory.TurSNInventoryEntry;
import com.viglet.turing.client.sn.inventory.TurSNInventoryException;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import com.viglet.turing.client.sn.manifest.TurSNManifest;
import com.viglet.turing.client.sn.manifest.TurSNManifestException;
import com.viglet.turing.client.sn.manifest.TurSNManifestResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Indexing plugin for Viglet Turing ES
 * 
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "dumont.indexing.provider", havingValue = "turing", matchIfMissing = true)
public class DumTuringIndexingPlugin implements DumIndexingPlugin {
    
    private final String turingUrl;
    private final String turingApiKey;

    public DumTuringIndexingPlugin(@Value("${turing.url}") String turingUrl,
            @Value("${turing.apiKey}") String turingApiKey) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
        log.info("Initialized Turing indexing plugin with URL: {}", turingUrl);
    }

    @Override
    public void index(TurSNJobItems turSNJobItems) {
        log.debug("Indexing {} items to Turing ES", turSNJobItems.getTuringDocuments().size());
        // D138: importItems logs a failed delivery and answers false; ignoring that recorded the batch
        // as indexed while Turing never received it.
        if (!TurSNJobUtils.importItems(turSNJobItems, getTurSNServer(), false)) {
            throw new DumIndexingDeliveryException("Turing did not accept a batch of %d item(s) at %s"
                    .formatted(turSNJobItems.getTuringDocuments().size(), turingUrl));
        }
    }

    @Override
    public String getProviderName() {
        return "TURING";
    }

    /**
     * Converges the site and its field schema through Turing's manifest endpoint before the first
     * document (roadmap <strong>D63</strong>). Re-posting the same manifest is a server-side no-op,
     * so this is safe on every run and needs no local "already provisioned" bookkeeping.
     * <p>
     * A rejected manifest does <em>not</em> stop the run. A {@code 409} means Turing kept the site's
     * current schema, so indexing proceeds exactly as it did before this call existed — with fields
     * inferred at index time. Refusing to index would take a working index offline over a schema
     * drift the operator may not be able to resolve at that moment, so the failure is reported
     * loudly, naming the fields that would have had to be rewritten, and the documents still flow.
     */
    @Override
    public Optional<String> provision(DumIndexingSchema schema) {
        TurSNManifest manifest = manifest(schema);
        try {
            TurSNManifestResult result = getTurSNServer().provision(manifest);
            if (result.isNoOp()) {
                log.debug("Site '{}' schema already matches the {} declared field(s) and {} custom "
                        + "facet(s)", schema.siteName(), schema.fields().size(),
                        schema.customFacets().size());
            } else {
                log.info("Provisioned site '{}' (created={}) from {} declared field(s); custom facets "
                        + "changed: {}", result.siteName(), result.siteCreated(),
                        schema.fields().size(), result.customFacetsChanged());
            }
            return Optional.empty();
        } catch (TurSNManifestException e) {
            if (e.isMigrationRequired()) {
                log.error("Site '{}' schema needs a declared migration — indexing continues with the "
                        + "site's current schema. Breaking change(s): {}", schema.siteName(),
                        e.getBreakingChanges());
                return Optional.of("site '%s' kept its current schema: the declared one needs a migration (%s)"
                        .formatted(schema.siteName(), e.getBreakingChanges()));
            }
            log.error("Could not provision site '{}': {} — indexing continues with the site's "
                    + "current schema", schema.siteName(), e.getMessage(), e);
            return Optional.of("site '%s' was not provisioned: %s".formatted(schema.siteName(),
                    e.getMessage()));
        }
    }

    /**
     * The manifest a schema converges to. Custom facets (D144) go on the same call as the fields;
     * the builder sends none when none is declared, which leaves the site's own untouched.
     */
    static TurSNManifest manifest(DumIndexingSchema schema) {
        TurSNManifest.Builder builder = TurSNManifest.builder()
                .name(schema.siteName())
                .locale(schema.locale())
                .fields(schema.fields());
        schema.customFacets().forEach(builder::customFacet);
        return builder.build();
    }

    /**
     * Enumerates what the index actually holds for a source (roadmap <strong>D64</strong>), walking
     * Turing's inventory cursor across every locale of the site.
     * <p>
     * An unreadable index comes back as {@link Optional#empty()}, never as an empty list. The
     * distinction is the whole point: the audit acts on a difference, and reporting a failed read as
     * "the index holds nothing" would have it republish the entire source.
     */
    @Override
    public Optional<List<DumIndexInventoryEntry>> inventory(String siteName, String source) {
        try {
            List<DumIndexInventoryEntry> entries = new ArrayList<>();
            for (TurSNInventoryEntry entry : new TurSNInventoryClient(getTurSNServer(), siteName,
                    TurSNInventoryClient.DEFAULT_ROWS).allLocales(source)) {
                entries.add(new DumIndexInventoryEntry(entry.id(), entry.version(),
                        entry.checksum()));
            }
            log.debug("Index inventory for site '{}' source '{}': {} document(s)", siteName, source,
                    entries.size());
            return Optional.of(entries);
        } catch (TurSNInventoryException e) {
            log.error("Could not read the index inventory for site '{}' source '{}': {}", siteName,
                    source, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private TurSNServer getTurSNServer() {
        return new TurSNServer(URI.create(turingUrl), null,
                new TurApiKeyCredentials(turingApiKey));
    }
}
