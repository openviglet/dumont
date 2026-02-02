package com.viglet.dumont.commons.logging;

import org.bson.Document;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Setter
public class DumMongoDBAppenderBase extends AppenderBase<ILoggingEvent> {
    protected boolean enabled;
    protected String connectionString;
    protected String databaseName;
    protected String collectionName;
    protected MongoCollection<Document> collection;
    protected com.mongodb.client.MongoClient mongoClient;

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        //
    }

    @Override
    public void start() {
        super.start();
        try {
            mongoClient = MongoClients.create(connectionString);
            collection = mongoClient
                    .getDatabase(databaseName)
                    .getCollection(collectionName);
        } catch (Exception e) {
            addError("Error connecting to MongoDB", e);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (mongoClient != null) {
            try {
                mongoClient.close();
            } catch (Exception e) {
                addError("Error closing MongoDB connection", e);
            }
        }
    }
}