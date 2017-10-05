package org.rtb.vexing.adapter;

import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;

import java.util.EnumMap;
import java.util.Objects;

public class AdapterCatalog {

    private final EnumMap<Adapter.Type, Adapter> adapters = new EnumMap<>(Adapter.Type.class);

    public AdapterCatalog(JsonObject config, HttpClient httpClient, PublicSuffixList psl) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(httpClient);
        Objects.requireNonNull(psl);

        final JsonObject adaptersConfig = Objects.requireNonNull(config.getJsonObject("adapters"));

        final JsonObject rubiconConfig = Objects.requireNonNull(adaptersConfig.getJsonObject("rubicon"));
        adapters.put(Adapter.Type.rubicon, new RubiconAdapter(
                getConfigValue(rubiconConfig, "endpoint"),
                getConfigValue(rubiconConfig, "usersync_url"),
                getConfigValue(rubiconConfig, "XAPI", "Username"),
                getConfigValue(rubiconConfig, "XAPI", "Password"),
                httpClient,
                psl));
    }

    public Adapter get(String code) {
        return adapters.get(Adapter.Type.valueOf(code));
    }

    private static String getConfigValue(JsonObject config, String... path) {
        JsonObject lowestNode = config;
        for (int i = 0; i < path.length - 1; i++) {
            lowestNode = Objects.requireNonNull(lowestNode.getJsonObject(path[i]));
        }
        return Objects.requireNonNull(lowestNode.getString(path[path.length - 1]));
    }
}
