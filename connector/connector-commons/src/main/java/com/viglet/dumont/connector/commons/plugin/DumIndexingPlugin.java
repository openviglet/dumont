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

package com.viglet.dumont.connector.commons.plugin;

import java.util.List;
import java.util.Optional;

import com.viglet.dumont.connector.commons.domain.DumIndexInventoryEntry;
import com.viglet.dumont.connector.commons.domain.DumIndexingSchema;
import com.viglet.turing.client.sn.job.TurSNJobItems;

/**
 * Interface for indexing plugins that allows Dumont to send content to different search engines
 * (Turing ES, Elasticsearch, Solr, etc.)
 * 
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
public interface DumIndexingPlugin {
    
    /**
     * Indexes the job items to the configured search engine
     * 
     * @param turSNJobItems the job items to be indexed
     */
    void index(TurSNJobItems turSNJobItems);

    /**
     * Gets the name of the indexing provider
     *
     * @return the provider name (e.g., "TURING", "SOLR", "ELASTICSEARCH")
     */
    String getProviderName();

    /**
     * Declares a source's field schema to the search engine <em>before</em> its documents (roadmap
     * <strong>D63</strong>), so fields exist as declared instead of being invented from the first
     * value that happens to arrive — a multi-valued field created single-valued, a facet created
     * without one, an optional field that does not exist until it appears.
     * <p>
     * A no-op by default: a backend with no declarative schema endpoint is unchanged, and its
     * indexing keeps whatever inference it did before.
     *
     * @param schema the site, locale and declared fields
     * @return why the backend did not take the schema, or empty when it did or had nothing to take
     *         (D139): a failed provisioning never stops a run, so the run record is where it shows
     */
    default Optional<String> provision(DumIndexingSchema schema) {
        // Backends without a declarative schema endpoint have nothing to send.
        return Optional.empty();
    }

    /**
     * Every document the index holds for one source on one site (roadmap <strong>D64</strong>), so a
     * content audit can make the comparison it could not: not "did we queue everything the source
     * has", but "did everything we queued actually land".
     * <p>
     * {@link Optional#empty()} means the backend cannot answer — it enumerates nothing, or the read
     * failed. That is deliberately distinct from a present but empty list, which means the index
     * genuinely holds nothing for this source: an audit that confused the two would read a failed
     * read as an empty index and republish everything.
     *
     * @param siteName the site to enumerate
     * @param source   the source whose documents to enumerate
     * @return the indexed documents, or empty when the backend cannot report them
     */
    default Optional<List<DumIndexInventoryEntry>> inventory(String siteName, String source) {
        return Optional.empty();
    }
}
