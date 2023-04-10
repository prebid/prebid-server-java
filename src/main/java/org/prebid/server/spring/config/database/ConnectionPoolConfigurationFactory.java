package org.prebid.server.spring.config.database;

import io.vertx.core.json.JsonObject;
import org.prebid.server.spring.config.database.model.ConnectionPoolSettings;

public interface ConnectionPoolConfigurationFactory {

    JsonObject create(String databaseUrl, ConnectionPoolSettings connectionPoolSettings);
}
