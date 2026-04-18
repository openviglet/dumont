/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * Authentication configuration properties for dumont.
 *
 * @since 2026.2.14
 */
@Getter
@Setter
public class DumAuthenticationProperty {
    private boolean thirdparty = true;
    private boolean newUser = false;
}
