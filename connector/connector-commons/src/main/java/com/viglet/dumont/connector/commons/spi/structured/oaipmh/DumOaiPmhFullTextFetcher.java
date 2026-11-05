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

package com.viglet.dumont.connector.commons.spi.structured.oaipmh;

import java.io.IOException;
import java.util.Optional;

/**
 * The seam a {@link DumOaiPmhSource} reads a record's full text through (roadmap
 * <strong>D315</strong>): a document URL the record names, on the repository's own host, in; its
 * text out. {@link DumHttpOaiPmhFullTextFetcher} is the production one; a test passes a lambda.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@FunctionalInterface
public interface DumOaiPmhFullTextFetcher {

    /**
     * The text of the PDF at {@code url}.
     *
     * @param url  an http(s) URL on {@code host}
     * @param host the only host the fetch may reach, a redirect included
     * @return the text, or empty when the URL serves no PDF, the PDF is over the size cap, or it
     *         holds no text
     * @throws IOException on a transport failure or an error status
     */
    Optional<String> text(String url, String host) throws IOException;
}
