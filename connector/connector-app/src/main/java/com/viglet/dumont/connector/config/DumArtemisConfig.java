/*
 *
 * Copyright (C) 2016-2025 the original author or authors.
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
package com.viglet.dumont.connector.config;

import static com.viglet.dumont.connector.constant.DumConnectorConstants.CONNECTOR_INDEXING_QUEUE;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.springframework.boot.artemis.autoconfigure.ArtemisConfigurationCustomizer;
import org.springframework.stereotype.Component;

/**
 * Explicit Artemis broker tuning to avoid producer flow-control hangs.
 *
 * Defaults in Artemis 2.x base globalMaxSize on the JVM's free heap at
 * startup, which combined with persistent journals can cause producers
 * to hang on credit acquisition between indexing runs even when the
 * queue is empty (the broker still accounts for memory not yet
 * reclaimed by GC, or for a near-full disk threshold).
 *
 * Pinning explicit limits and using PAGE policy keeps producers
 * non-blocking: when the address is full, messages spill to disk
 * instead of blocking the producer.
 */
@Component
public class DumArtemisConfig implements ArtemisConfigurationCustomizer {

    private static final long ONE_GB = 1024L * 1024L * 1024L;
    private static final long FIVE_HUNDRED_MB = 512L * 1024L * 1024L;
    private static final int TEN_MB = 10 * 1024 * 1024;

    @Override
    public void customize(Configuration configuration) {
        configuration.setGlobalMaxSize(ONE_GB);
        configuration.setMaxDiskUsage(95);

        AddressSettings settings = new AddressSettings()
                .setMaxSizeBytes(FIVE_HUNDRED_MB)
                .setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE)
                .setPageSizeBytes(TEN_MB);

        configuration.getAddressSettings().put(CONNECTOR_INDEXING_QUEUE, settings);
        configuration.getAddressSettings().put("#", settings);
    }
}
