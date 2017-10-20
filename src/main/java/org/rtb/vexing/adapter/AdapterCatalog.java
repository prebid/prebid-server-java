package org.rtb.vexing.adapter;

import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import io.vertx.core.http.HttpClient;
import org.rtb.vexing.adapter.rubicon.RubiconAdapter;
import org.rtb.vexing.config.ApplicationConfig;

import java.util.EnumMap;
import java.util.Objects;

public class AdapterCatalog {

    private final EnumMap<Adapter.Type, Adapter> adapters = new EnumMap<>(Adapter.Type.class);

    public AdapterCatalog(ApplicationConfig config, HttpClient httpClient, PublicSuffixList psl) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(httpClient);
        Objects.requireNonNull(psl);

        adapters.put(Adapter.Type.rubicon, new RubiconAdapter(
                config.getString("adapters.rubicon.endpoint"),
                config.getString("adapters.rubicon.usersync_url"),
                config.getString("adapters.rubicon.XAPI.Username"),
                config.getString("adapters.rubicon.XAPI.Password"),
                httpClient,
                psl));
    }

    public Adapter get(String code) {
        return adapters.get(Adapter.Type.valueOf(code));
    }
}
