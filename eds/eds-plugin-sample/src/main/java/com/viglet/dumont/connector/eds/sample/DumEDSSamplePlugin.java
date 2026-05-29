/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.dumont.connector.eds.sample;

import org.pf4j.Plugin;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumEDSSamplePlugin extends Plugin {

    @Override
    public void start() {
        log.info("eds-plugin-sample started");
    }

    @Override
    public void stop() {
        log.info("eds-plugin-sample stopped");
    }
}
