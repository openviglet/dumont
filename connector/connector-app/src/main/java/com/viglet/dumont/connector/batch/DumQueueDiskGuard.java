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

package com.viglet.dumont.connector.batch;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.config.DumArtemisConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Refuses a send the embedded broker would block forever (roadmap <strong>D341</strong>).
 *
 * <p>
 * Above {@link DumArtemisConfig#MAX_DISK_USAGE} percent of its disk, Artemis stops granting
 * producer credits. The send then waits with no error and no timeout, holding whichever thread
 * asked for it, and a run stalls after "Draining N jobs from buffer". Reading the same figure the
 * broker reads, before the send, turns that silent wait into a failed run that says why.
 */
@Slf4j
@Component
public class DumQueueDiskGuard {

    private final Path directory;
    private final Function<Path, OptionalDouble> usedPercent;

    @Autowired
    public DumQueueDiskGuard(
            @Value("${spring.artemis.embedded.data-directory:store/queue}") String directory) {
        this(Path.of(directory), DumQueueDiskGuard::usedPercentOf);
    }

    DumQueueDiskGuard(Path directory, Function<Path, OptionalDouble> usedPercent) {
        this.directory = directory;
        this.usedPercent = usedPercent;
    }

    /**
     * @throws DumQueueDiskFullException when the queue's disk is at or over the broker's limit
     */
    public void check() {
        OptionalDouble used = usedPercent.apply(directory);
        if (used.isPresent() && used.getAsDouble() >= DumArtemisConfig.MAX_DISK_USAGE) {
            throw new DumQueueDiskFullException(
                    "the queue's disk is over its limit: %.0f%% used, and the embedded queue stops at %d%% (%s)"
                            .formatted(used.getAsDouble(), DumArtemisConfig.MAX_DISK_USAGE,
                                    directory.toAbsolutePath()));
        }
    }

    /** The share of the disk holding {@code directory} that is used, as Artemis computes it. */
    static OptionalDouble usedPercentOf(Path directory) {
        Path existing = directory.toAbsolutePath();
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return OptionalDouble.empty();
        }
        try {
            FileStore store = Files.getFileStore(existing);
            long total = store.getTotalSpace();
            if (total <= 0) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of((total - store.getUsableSpace()) * 100.0 / total);
        } catch (IOException e) {
            log.debug("Cannot read the disk usage of {}: {}", existing, e.getMessage());
            return OptionalDouble.empty();
        }
    }
}
