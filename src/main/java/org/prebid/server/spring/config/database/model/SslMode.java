package org.prebid.server.spring.config.database.model;

import lombok.Getter;

@Getter
public enum SslMode {

    disabled(io.vertx.pgclient.SslMode.DISABLE, io.vertx.mysqlclient.SslMode.DISABLED),
    preferred(io.vertx.pgclient.SslMode.PREFER, io.vertx.mysqlclient.SslMode.PREFERRED),
    required(io.vertx.pgclient.SslMode.REQUIRE, io.vertx.mysqlclient.SslMode.REQUIRED);

    public final io.vertx.pgclient.SslMode pgMode;
    public final io.vertx.mysqlclient.SslMode mysqlMode;

    SslMode(io.vertx.pgclient.SslMode pgMode, io.vertx.mysqlclient.SslMode mysqlMode) {
        this.pgMode = pgMode;
        this.mysqlMode = mysqlMode;
    }
}
