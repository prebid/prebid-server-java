package org.prebid.server.spring.config.database;

public interface DatabaseUrlFactory {

    String createUrl(String host, int port, String databaseName);
}
