package com.viglet.dumont.aem.server.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Dumont Indexer Configuration")
public @interface DumAemIndexerConfig {
    @AttributeDefinition(name = "Enabled", description = "Enables to run the Dumont Event Handler")
    boolean enabled() default false;

    @AttributeDefinition(name = "Dumont API - Host")
    String host() default "";

    @AttributeDefinition(name = "Dumont Connector Config Name")
    String configName() default "";
}
