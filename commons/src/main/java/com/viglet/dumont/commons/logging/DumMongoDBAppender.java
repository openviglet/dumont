package com.viglet.dumont.commons.logging;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import com.fasterxml.jackson.annotation.JsonFormat;

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

    // Recomendação: No Jackson 3, o mapper é imutável. Criá-lo como estático é
    // muito mais eficiente.
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .addModule(new JodaModule())
            .withConfigOverride(Date.class, cfg -> cfg.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.NUMBER)))
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
                .date(new Date(event.getTimeStamp()))
                .stackTrace(getStackTrace(event))
                .build();
        try {
            dumLoggingGeneral.setClusterNode(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            log.error(e.getMessage(), e);
        }
        String json = JSON_MAPPER.writeValueAsString(dumLoggingGeneral);
        collection.insertOne(Document.parse(json));
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
        if (packageName.length() <= MAX_LENGTH_PACKAGE_NAME)
            return packageName;
        // StringBuffer -> StringBuilder (mais moderno se não houver concorrência local)
        StringBuilder stringBuilder = new StringBuilder(packageName);
        DumNameAbbreviator.getAbbreviator("1.").abbreviate(1, stringBuilder);
        return stringBuilder.toString();
    }
}