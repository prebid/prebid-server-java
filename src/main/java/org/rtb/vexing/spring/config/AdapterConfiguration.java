package org.rtb.vexing.spring.config;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.adapter.appnexus.AppnexusAdapter;
import org.rtb.vexing.adapter.conversant.ConversantAdapter;
import org.rtb.vexing.adapter.facebook.FacebookAdapter;
import org.rtb.vexing.adapter.index.IndexAdapter;
import org.rtb.vexing.adapter.lifestreet.LifestreetAdapter;
import org.rtb.vexing.adapter.pubmatic.PubmaticAdapter;
import org.rtb.vexing.adapter.pulsepoint.PulsepointAdapter;
import org.rtb.vexing.adapter.rubicon.RubiconAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class AdapterConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AdapterConfiguration.class);

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
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
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    RubiconAdapter rubiconAdapter(
            @Value("${adapters.rubicon.endpoint}") String endpoint,
            @Value("${adapters.rubicon.usersync-url}") String usersyncUrl,
            @Value("${adapters.rubicon.XAPI.Username}") String username,
            @Value("${adapters.rubicon.XAPI.Password}") String password) {

        return new RubiconAdapter(endpoint, usersyncUrl, username, password);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    AppnexusAdapter appnexusAdapter(
            @Value("${adapters.appnexus.endpoint}") String endpoint,
            @Value("${adapters.appnexus.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {

        return new AppnexusAdapter(endpoint, usersyncUrl, externalUrl);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    PulsepointAdapter pulsepointAdapter(
            @Value("${adapters.pulsepoint.endpoint}") String endpoint,
            @Value("${adapters.pulsepoint.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {

        return new PulsepointAdapter(endpoint, usersyncUrl, externalUrl);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @ConditionalOnProperty(name = {"adapters.facebook.usersync-url", "adapters.facebook.platformId"})
    FacebookAdapter facebookAdapter(
            @Value("${adapters.facebook.endpoint}") String endpoint,
            @Value("${adapters.facebook.nonSecureEndpoint}") String nonSecureEndpoint,
            @Value("${adapters.facebook.usersync-url}") String usersyncUrl,
            @Value("${adapters.facebook.platformId}") String platformId) {

        return new FacebookAdapter(endpoint, nonSecureEndpoint, usersyncUrl, platformId);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @ConditionalOnProperty(name = "adapters.indexexchange.endpoint")
    IndexAdapter indexAdapter(
            @Value("${adapters.indexexchange.endpoint}") String endpoint,
            @Value("${adapters.indexexchange.usersync-url}") String usersyncUrl) {

        return new IndexAdapter(endpoint, usersyncUrl);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    LifestreetAdapter lifestreetAdapter(
            @Value("${adapters.lifestreet.endpoint}") String endpoint,
            @Value("${adapters.lifestreet.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {

        return new LifestreetAdapter(endpoint, usersyncUrl, externalUrl);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    PubmaticAdapter pubmaticAdapter(
            @Value("${adapters.pubmatic.endpoint}") String endpoint,
            @Value("${adapters.pubmatic.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {

        return new PubmaticAdapter(endpoint, usersyncUrl, externalUrl);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    ConversantAdapter conversantAdapter(
            @Value("${adapters.conversant.endpoint}") String endpoint,
            @Value("${adapters.conversant.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {

        return new ConversantAdapter(endpoint, usersyncUrl, externalUrl);
    }
}
