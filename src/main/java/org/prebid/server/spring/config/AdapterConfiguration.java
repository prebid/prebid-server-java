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
import org.prebid.server.usersyncer.AppnexusUsersyncer;
import org.prebid.server.usersyncer.ConversantUsersyncer;
import org.prebid.server.usersyncer.FacebookUsersyncer;
import org.prebid.server.usersyncer.IndexUsersyncer;
import org.prebid.server.usersyncer.LifestreetUsersyncer;
import org.prebid.server.usersyncer.PubmaticUsersyncer;
import org.prebid.server.usersyncer.PulsepointUsersyncer;
import org.prebid.server.usersyncer.RubiconUsersyncer;
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
    AppnexusAdapter appnexusAdapter(
            AppnexusUsersyncer appnexusUsersyncer,
            @Value("${adapters.appnexus.endpoint}") String endpoint) {

        return new AppnexusAdapter(appnexusUsersyncer, endpoint);
    }

    @Bean
    ConversantAdapter conversantAdapter(
            ConversantUsersyncer conversantUsersyncer,
            @Value("${adapters.conversant.endpoint}") String endpoint) {

        return new ConversantAdapter(conversantUsersyncer, endpoint);
    }

    @Bean
    @ConditionalOnProperty(name = {"adapters.facebook.usersync-url", "adapters.facebook.platformId"})
    FacebookAdapter facebookAdapter(
            FacebookUsersyncer facebookUsersyncer,
            @Value("${adapters.facebook.endpoint}") String endpoint,
            @Value("${adapters.facebook.nonSecureEndpoint}") String nonSecureEndpoint,
            @Value("${adapters.facebook.platformId}") String platformId) {

        return new FacebookAdapter(facebookUsersyncer, endpoint, nonSecureEndpoint, platformId);
    }

    @Bean
    @ConditionalOnProperty(name = "adapters.indexexchange.endpoint")
    IndexAdapter indexAdapter(
            IndexUsersyncer indexUsersyncer,
            @Value("${adapters.indexexchange.endpoint}") String endpoint) {

        return new IndexAdapter(indexUsersyncer, endpoint);
    }

    @Bean
    LifestreetAdapter lifestreetAdapter(
            LifestreetUsersyncer lifestreetUsersyncer,
            @Value("${adapters.lifestreet.endpoint}") String endpoint) {

        return new LifestreetAdapter(lifestreetUsersyncer, endpoint);
    }

    @Bean
    PubmaticAdapter pubmaticAdapter(
            PubmaticUsersyncer pubmaticUsersyncer,
            @Value("${adapters.pubmatic.endpoint}") String endpoint) {

        return new PubmaticAdapter(pubmaticUsersyncer, endpoint);
    }

    @Bean
    PulsepointAdapter pulsepointAdapter(
            PulsepointUsersyncer pulsepointUsersyncer,
            @Value("${adapters.pulsepoint.endpoint}") String endpoint) {

        return new PulsepointAdapter(pulsepointUsersyncer, endpoint);
    }

    @Bean
    RubiconAdapter rubiconAdapter(
            RubiconUsersyncer rubiconUsersyncer,
            @Value("${adapters.rubicon.endpoint}") String endpoint,
            @Value("${adapters.rubicon.XAPI.Username}") String username,
            @Value("${adapters.rubicon.XAPI.Password}") String password) {

        return new RubiconAdapter(rubiconUsersyncer, endpoint, username, password);
    }
}
