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
 * What the DSpace profile of an OAI-PMH source (roadmap <strong>D314</strong>) reads the
 * repository's REST API with. Both calls stay on {@code host}, a redirect included; a test passes
 * canned answers.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface DumDSpaceRest {

    /**
     * Requests a JSON resource. A non-2xx status is an answer, not an exception, so the caller can
     * tell a private item (401/403) from a missing one (404).
     *
     * @throws IOException on a transport failure, or a redirect off {@code host}
     */
    Response get(String url, String host) throws IOException;

    /**
     * The text of a bitstream's content: DSpace's own extracted text, or a PDF read through the
     * shared, capped text extractor.
     *
     * @return the text, or empty when the content is neither text nor a PDF, is over the size cap,
     *         or holds no text
     * @throws IOException on a transport failure or an error status — a 401/403 included, which is
     *                     a bitstream the public may not read
     */
    Optional<String> text(String url, String host) throws IOException;

    /** One JSON answer: its status, and its body when there was one. */
    record Response(int status, String body) {

        public boolean ok() {
            return status / 100 == 2;
        }

        /** A private or restricted resource: the anonymous harvester may not read it. */
        public boolean denied() {
            return status == 401 || status == 403;
        }
    }
}
