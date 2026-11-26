/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.dumont.connector.commons.exchange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.viglet.dumont.connector.commons.DumSourceNames;

/**
 * What a replacing import does to the sources of one connector type (roadmap <strong>DU9</strong>),
 * matched by name: the file's sources that are new are {@code create}d, the ones that already exist
 * are {@code overwrite}n, and existing sources the file does not name are {@code remove}d.
 *
 * <p>{@code invalid} lists the file's names that cannot be saved: blank, repeated within the file, or
 * (where the connector keys its URLs by name) holding whitespace. A plan with any is refused whole,
 * never half-applied. The same plan answers the dry run the console shows before it replaces.
 */
public record DumSourceReplacePlan(List<String> create, List<String> overwrite, List<String> remove,
        List<String> invalid) {

    /**
     * Matches the names in a file against the names an instance already has.
     *
     * @param fileNames       the file's source names, in file order
     * @param existingNames   the names of the sources the instance has
     * @param namesAreUrlKeys whether the name is the source key in the indexing URLs, so the
     *                        {@link DumSourceNames} whitespace rule applies (web-crawler titles are
     *                        display-only and pass {@code false})
     */
    public static DumSourceReplacePlan of(List<String> fileNames, Collection<String> existingNames,
            boolean namesAreUrlKeys) {
        Set<String> existing = new HashSet<>(existingNames);
        Set<String> seen = new HashSet<>();
        List<String> create = new ArrayList<>();
        List<String> overwrite = new ArrayList<>();
        Set<String> invalid = new LinkedHashSet<>();
        for (String name : fileNames) {
            if (name == null || name.isBlank()) {
                invalid.add(String.valueOf(name));
            } else if (namesAreUrlKeys && !DumSourceNames.isValid(name)) {
                invalid.add(name);
            } else if (!seen.add(name)) {
                invalid.add(name);
            } else if (existing.contains(name)) {
                overwrite.add(name);
            } else {
                create.add(name);
            }
        }
        List<String> remove = existingNames.stream().filter(name -> !seen.contains(name)).toList();
        return new DumSourceReplacePlan(List.copyOf(create), List.copyOf(overwrite), List.copyOf(remove),
                List.copyOf(invalid));
    }

    /** Whether every name in the file can be saved, so the replace may go ahead. */
    public boolean isApplicable() {
        return invalid.isEmpty();
    }

    /** The refusal for a plan that is not applicable, naming every offending entry. */
    public DumSourceImportException refusal() {
        return new DumSourceImportException("The file was not imported, nothing was changed: these source names"
                + " are blank, repeated, or hold whitespace: " + String.join(", ", invalid));
    }
}
