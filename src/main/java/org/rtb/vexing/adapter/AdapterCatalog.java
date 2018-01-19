package org.rtb.vexing.adapter;

import io.vertx.core.http.HttpClient;
import org.rtb.vexing.adapter.appnexus.AppnexusAdapter;
import org.rtb.vexing.adapter.conversant.ConversantAdapter;
import org.rtb.vexing.adapter.facebook.FacebookAdapter;
import org.rtb.vexing.adapter.indexexchange.IndexExchangeAdapter;
import org.rtb.vexing.adapter.lifestreet.LifestreetAdapter;
import org.rtb.vexing.adapter.pubmatic.PubmaticAdapter;
import org.rtb.vexing.adapter.pulsepoint.PulsepointAdapter;
import org.rtb.vexing.adapter.rubicon.RubiconAdapter;
import org.rtb.vexing.config.ApplicationConfig;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AdapterCatalog {

    private final Map<String, Adapter> adapters;

    private AdapterCatalog(Map<String, Adapter> adapters) {
        this.adapters = adapters;
    }

    public static AdapterCatalog create(ApplicationConfig config, HttpClient httpClient) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(httpClient);

        final Map<String, Adapter> adapters = Stream.of(
                rubicon(config, httpClient),
                appnexus(config, httpClient),
                facebook(config, httpClient),
                pulsepoint(config, httpClient),
                index(config, httpClient),
                lifestreet(config, httpClient),
                pubmatic(config, httpClient),
                conversant(config, httpClient))
                .collect(Collectors.toMap(Adapter::code, Function.identity()));

        return new AdapterCatalog(adapters);
    }

    private static RubiconAdapter rubicon(ApplicationConfig config, HttpClient httpClient) {
        return new RubiconAdapter(
                config.getString("adapters.rubicon.endpoint"),
                config.getString("adapters.rubicon.usersync_url"),
                config.getString("adapters.rubicon.XAPI.Username"),
                config.getString("adapters.rubicon.XAPI.Password"),
                httpClient);
    }

    private static AppnexusAdapter appnexus(ApplicationConfig config, HttpClient httpClient) {
        return new AppnexusAdapter(
                config.getString("adapters.appnexus.endpoint"),
                config.getString("adapters.appnexus.usersync_url"),
                config.getString("external_url"),
                httpClient);
    }

    private static FacebookAdapter facebook(ApplicationConfig config, HttpClient httpClient) {
        return new FacebookAdapter(
                config.getString("adapters.facebook.endpoint"),
                config.getString("adapters.facebook.nonSecureEndpoint"),
                config.getString("adapters.facebook.usersync_url"),
                config.getString("adapters.facebook.platform_id"),
                httpClient);
    }

    private static PulsepointAdapter pulsepoint(ApplicationConfig config, HttpClient httpClient) {
        return new PulsepointAdapter(
                config.getString("adapters.pulsepoint.endpoint"),
                config.getString("adapters.pulsepoint.usersync_url"),
                config.getString("external_url"),
                httpClient);
    }

    private static IndexExchangeAdapter index(ApplicationConfig config, HttpClient httpClient) {
        return new IndexExchangeAdapter(
                config.getString("adapters.indexexchange.endpoint"),
                config.getString("adapters.indexexchange.usersync_url"),
                httpClient);
    }

    private static LifestreetAdapter lifestreet(ApplicationConfig config, HttpClient httpClient) {
        return new LifestreetAdapter(
                config.getString("adapters.lifestreet.endpoint"),
                config.getString("adapters.lifestreet.usersync_url"),
                config.getString("external_url"),
                httpClient);
    }

    private static PubmaticAdapter pubmatic(ApplicationConfig config, HttpClient httpClient) {
        return new PubmaticAdapter(
                config.getString("adapters.pubmatic.endpoint"),
                config.getString("adapters.pubmatic.usersync_url"),
                config.getString("external_url"),
                httpClient);
    }

    private static ConversantAdapter conversant(ApplicationConfig config, HttpClient httpClient) {
        return new ConversantAdapter(
                config.getString("adapters.conversant.endpoint"),
                config.getString("adapters.conversant.usersync_url"),
                config.getString("external_url"),
                httpClient);
    }

    public Adapter getByCode(String code) {
        return adapters.get(code);
    }

    public boolean isValidCode(String code) {
        return adapters.containsKey(code);
    }
}
