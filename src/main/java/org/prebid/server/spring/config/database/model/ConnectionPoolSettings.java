package org.prebid.server.spring.config.database.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class ConnectionPoolSettings {

    Integer poolSize;

    String user;

    String password;

    DatabaseType databaseType;
}
