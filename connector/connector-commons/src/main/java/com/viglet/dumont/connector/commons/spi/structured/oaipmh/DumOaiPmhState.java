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

import java.util.Optional;

/**
 * What one OAI-PMH source remembers between runs (roadmap <strong>D315</strong>): the
 * {@code responseDate} of the last harvest that completed. The next run asks the repository only
 * for what changed since then, so the watermark has to outlive the run that set it.
 *
 * <p>
 * One instance serves one source. {@link DumInMemoryOaiPmhState} is the default and forgets on
 * restart, which costs one full harvest; a plugin that owns a database passes a persistent one.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface DumOaiPmhState {

    /** The {@code responseDate} of the last complete harvest, empty before the first one. */
    Optional<String> watermark();

    /** Records the {@code responseDate} of a harvest that completed. */
    void watermark(String responseDate);
}
