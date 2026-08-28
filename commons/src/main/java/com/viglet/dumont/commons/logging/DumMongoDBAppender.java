package com.viglet.dumont.commons.logging;

import java.time.Instant;

import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import ch.qos.logback.classic.pattern.Abbreviator;
import ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.CoreConstants;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.joda.JodaModule;

@Slf4j
@Setter
public class DumMongoDBAppender extends DumMongoDBAppenderBase {

    public static final int MAX_LENGTH_PACKAGE_NAME = 40;
    private static final Abbreviator ABBREVIATOR = new TargetLengthBasedClassNameAbbreviator(1);

    // Recomendação: No Jackson 3, o mapper é imutável. Criá-lo como estático é
    // muito mais eficiente.
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .addModule(new JodaModule())
            .build();

    @Override
    protected void append(ILoggingEvent event) {
        if (!enabled || collection == null) {
            return;
        }
        DumLoggingGeneral dumLoggingGeneral = DumLoggingGeneral.builder()
                .level(event.getLevel().toString())
                .logger(abbreviatePackage(event.getLoggerName()))
                .message(event.getFormattedMessage())
                .date(Instant.ofEpochMilli(event.getTimeStamp()))
                .stackTrace(getStackTrace(event))
                // D80: read, not resolved. This used to be a name-service call per event, on the
                // async worker that drains this appender — so a slow resolver did not slow the
                // application, it filled the queue and made logback discard log lines instead.
                .clusterNode(DumClusterNode.NAME)
                .build();
        String json = JSON_MAPPER.writeValueAsString(dumLoggingGeneral);
        // D73: the same date conversion the indexing appender does — the serializer emits JSON
        // now, so the BSON date is made here rather than smuggled through Mongo shell syntax.
        collection.insertOne(DumMongoDBIndexingAppender.withBsonDate(Document.parse(json)));
    }

    private static @NotNull String getStackTrace(ILoggingEvent event) {
        StringBuilder stackStraceBuilder = new StringBuilder();
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            String throwableStr = ThrowableProxyUtil.asString(throwableProxy);
            stackStraceBuilder.append(throwableStr);
            stackStraceBuilder.append(CoreConstants.LINE_SEPARATOR);
        }
        return stackStraceBuilder.toString();
    }

    private static @NotNull String abbreviatePackage(String packageName) {
        if (packageName == null)
            return "";
        if (packageName.length() <= MAX_LENGTH_PACKAGE_NAME)
            return packageName;
        return ABBREVIATOR.abbreviate(packageName);
    }
}