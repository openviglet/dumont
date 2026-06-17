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

package com.viglet.dumont.connector.commons.spi.structured;

import java.util.Optional;

import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * The role a {@link DumStructuredField} plays in Turing's index-time embedding.
 *
 * <p>
 * Turing already generates embeddings at index time on the path Dumont uses
 * ({@code POST /api/sn/import}), but its partitioner only recognizes the canonical keys
 * {@code title}, {@code abstract} and {@code text}: {@code title} becomes the chunk header, and
 * {@code abstract}/{@code text} become the embedded body. A structured source whose meaningful
 * content lives under custom field names (e.g. {@code course_name}, {@code ementa}) is therefore
 * embedded poorly or not at all.
 *
 * <p>
 * Declaring an embedding role on a field lets the publish step <em>project</em> that field's value
 * onto the canonical key the partitioner reads — without renaming the source field (it stays for
 * display and facets) and without ever fabricating content. This is roadmap <strong>D17</strong>;
 * the partition contract it targets is documented in {@code docs/EMBEDDING_PARTITION_CONTRACT.md}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum DumEmbeddingRole {

    /** Default — the field is not projected; it stays metadata/facet only. */
    NONE(null),

    /** The document's primary heading — projected onto Turing's {@code title}. */
    TITLE(TurSNFieldName.TITLE),

    /** A short summary — projected onto Turing's {@code abstract}. */
    SUMMARY(TurSNFieldName.ABSTRACT),

    /** Long-form content to embed — projected onto Turing's {@code text}. */
    BODY(TurSNFieldName.TEXT);

    private final String targetAttribute;

    DumEmbeddingRole(String targetAttribute) {
        this.targetAttribute = targetAttribute;
    }

    /** The canonical Turing attribute this role projects onto, or empty for {@link #NONE}. */
    public Optional<String> targetAttribute() {
        return Optional.ofNullable(targetAttribute);
    }

    /** Whether this role projects the field's value onto a canonical embedding key. */
    public boolean projects() {
        return targetAttribute != null;
    }
}
