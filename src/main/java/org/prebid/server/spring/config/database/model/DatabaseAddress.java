package org.prebid.server.spring.config.database.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class DatabaseAddress {

    String host;

    int port;

    String databaseName;
}
