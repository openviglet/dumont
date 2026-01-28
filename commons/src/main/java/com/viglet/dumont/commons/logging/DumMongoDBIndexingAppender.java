package com.viglet.dumont.commons.logging;

import java.util.Arrays;

import org.bson.Document;

import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Setter
public class DumMongoDBIndexingAppender extends DumMongoDBAppenderBase {

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!enabled || collection == null || eventObject.getArgumentArray() == null) {
            return;
        }
        ObjectMapper mapper = JsonMapper.builder().build();
        Arrays.stream(eventObject.getArgumentArray()).forEach(object -> {
            String json = mapper.writeValueAsString(object);
            collection.insertOne(Document.parse(json));

        });
    }
}