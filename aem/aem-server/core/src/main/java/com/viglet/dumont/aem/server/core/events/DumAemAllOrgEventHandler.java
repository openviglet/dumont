package com.viglet.dumont.aem.server.core.events;

import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

/**
 * Choosing not writing a configuration will disable the component, so it will not log all events.
 */
@Slf4j
@Component(service = EventHandler.class, immediate = true, property = {
        Constants.SERVICE_DESCRIPTION + "=Listen to all org node changes",
        EventConstants.EVENT_TOPIC + "=org/*" }, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class DumAemAllOrgEventHandler implements EventHandler {
    @Override
    public void handleEvent(Event event) {
        log.info("Dumont Log All Org Event: {}", event);
    }
}
