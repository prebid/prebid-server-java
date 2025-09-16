package org.prebid.server.spring.config.database.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class ConnectionPoolSettings {

    Integer poolSize;

    Integer idleTimeout;

    Boolean enablePreparedStatementCaching;

    Integer maxPreparedStatementCacheSize;

    String user;

    String password;

    DatabaseType databaseType;
}
