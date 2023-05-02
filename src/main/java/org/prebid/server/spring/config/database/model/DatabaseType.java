package org.prebid.server.spring.config.database.model;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum DatabaseType {

    postgres("org.postgresql.Driver"),
    mysql("com.mysql.cj.jdbc.Driver");

    public final String jdbcDriver;
}
