package org.prebid.server.spring.config;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.adapter.Adapter;
import org.prebid.server.adapter.AdapterCatalog;
import org.prebid.server.adapter.appnexus.AppnexusAdapter;
import org.prebid.server.adapter.conversant.ConversantAdapter;
import org.prebid.server.adapter.facebook.FacebookAdapter;
import org.prebid.server.adapter.index.IndexAdapter;
import org.prebid.server.adapter.lifestreet.LifestreetAdapter;
import org.prebid.server.adapter.pubmatic.PubmaticAdapter;
import org.prebid.server.adapter.pulsepoint.PulsepointAdapter;
import org.prebid.server.adapter.rubicon.RubiconAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class AdapterConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AdapterConfiguration.class);

    @Bean
    AdapterCatalog adapterCatalog(List<Adapter> adapters) {

        // There are no default values for some adapter properties. We don't want to force their presence in external
        // configuration and just skip adapters with incomplete configuration. But we want to make anyone deploying
        // the application aware of that nuisance.
        final List<Class<?>> adapterClasses = adapters.stream().map(Object::getClass).collect(Collectors.toList());
        Stream.of(FacebookAdapter.class, IndexAdapter.class)
                .filter(clazz -> !adapterClasses.contains(clazz))
                .forEach(clazz -> logger.warn("{0} has not been initialized due to missing configuration properties. "
                        + "Please review configuration.", clazz.getSimpleName()));

        return new AdapterCatalog(adapters);
    }

    @Bean
    RubiconAdapter rubiconAdapter(
            @Value("${adapters.rubicon.endpoint}") String endpoint,
            @Value("${adapters.rubicon.usersync-url}") String usersyncUrl,
            @Value("${adapters.rubicon.XAPI.Username}") String username,
            @Value("${adapters.rubicon.XAPI.Password}") String password) {

        return new RubiconAdapter(endpoint, usersyncUrl, username, password);
    }

    @Bean
    AppnexusAdapter appnexusAdapter(
            @Value("${adapters.appnexus.endpoint}") String endpoint,
            @Value("${adapters.appnexus.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {

        return new AppnexusAdapter(endpoint, usersyncUrl, externalUrl);
    }

    @Bean
    PulsepointAdapter pulsepointAdapter(
            @Value("${adapters.pulsepoint.endpoint}") String endpoint,
            @Value("${adapters.pulsepoint.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {

        return new PulsepointAdapter(endpoint, usersyncUrl, externalUrl);
    }

    @Bean
    @ConditionalOnProperty(name = {"adapters.facebook.usersync-url", "adapters.facebook.platformId"})
    FacebookAdapter facebookAdapter(
            @Value("${adapters.facebook.endpoint}") String endpoint,
            @Value("${adapters.facebook.nonSecureEndpoint}") String nonSecureEndpoint,
            @Value("${adapters.facebook.usersync-url}") String usersyncUrl,
            @Value("${adapters.facebook.platformId}") String platformId) {

        return new FacebookAdapter(endpoint, nonSecureEndpoint, usersyncUrl, platformId);
    }

    @Bean
    @ConditionalOnProperty(name = "adapters.indexexchange.endpoint")
    IndexAdapter indexAdapter(
            @Value("${adapters.indexexchange.endpoint}") String endpoint,
            @Value("${adapters.indexexchange.usersync-url}") String usersyncUrl) {

        return new IndexAdapter(endpoint, usersyncUrl);
    }

    @Bean
    LifestreetAdapter lifestreetAdapter(
            @Value("${adapters.lifestreet.endpoint}") String endpoint,
            @Value("${adapters.lifestreet.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {

        return new LifestreetAdapter(endpoint, usersyncUrl, externalUrl);
    }

    @Bean
    PubmaticAdapter pubmaticAdapter(
            @Value("${adapters.pubmatic.endpoint}") String endpoint,
            @Value("${adapters.pubmatic.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {

        return new PubmaticAdapter(endpoint, usersyncUrl, externalUrl);
    }

    @Bean
    ConversantAdapter conversantAdapter(
            @Value("${adapters.conversant.endpoint}") String endpoint,
            @Value("${adapters.conversant.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {

        return new ConversantAdapter(endpoint, usersyncUrl, externalUrl);
    }
}
