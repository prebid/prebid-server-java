package org.prebid.server.spring.config;

import org.prebid.server.auction.BidderCatalog;
import org.prebid.server.usersyncer.AppnexusUsersyncer;
import org.prebid.server.usersyncer.ConversantUsersyncer;
import org.prebid.server.usersyncer.FacebookUsersyncer;
import org.prebid.server.usersyncer.IndexUsersyncer;
import org.prebid.server.usersyncer.LifestreetUsersyncer;
import org.prebid.server.usersyncer.PubmaticUsersyncer;
import org.prebid.server.usersyncer.PulsepointUsersyncer;
import org.prebid.server.usersyncer.RubiconUsersyncer;
import org.prebid.server.usersyncer.Usersyncer;
import org.prebid.server.usersyncer.UsersyncerCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class UsersyncerConfiguration {

    @Bean
    UsersyncerCatalog usersyncerCatalog(List<Usersyncer> usersyncers, BidderCatalog bidderCatalog) {
        final UsersyncerCatalog usersyncerCatalog = new UsersyncerCatalog(usersyncers);

        // check missing usersyncers
        for (String name : bidderCatalog.names()) {
            if (!usersyncerCatalog.isValidName(name)) {
                throw new IllegalStateException(
                        String.format("Usersyncer not found for %s bidder. "
                                + "Please review documentation how to add new bidder.", name));
            }
        }

        return usersyncerCatalog;
    }

    @Bean
    AppnexusUsersyncer appnexusUsersyncer(
            @Value("${adapters.appnexus.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {
        return new AppnexusUsersyncer(usersyncUrl, externalUrl);
    }

    @Bean
    ConversantUsersyncer conversantUsersyncer(
            @Value("${adapters.conversant.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {
        return new ConversantUsersyncer(usersyncUrl, externalUrl);
    }

    @Bean
    @ConditionalOnProperty(name = {"adapters.facebook.usersync-url", "adapters.facebook.platformId"})
    FacebookUsersyncer facebookUsersyncer(
            @Value("${adapters.facebook.usersync-url}") String usersyncUrl) {
        return new FacebookUsersyncer(usersyncUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "adapters.indexexchange.endpoint")
    IndexUsersyncer indexUsersyncer(
            @Value("${adapters.indexexchange.usersync-url}") String usersyncUrl) {
        return new IndexUsersyncer(usersyncUrl);
    }

    @Bean
    LifestreetUsersyncer lifestreetUsersyncer(
            @Value("${adapters.lifestreet.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {
        return new LifestreetUsersyncer(usersyncUrl, externalUrl);
    }

    @Bean
    PubmaticUsersyncer pubmaticUsersyncer(
            @Value("${adapters.pubmatic.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {
        return new PubmaticUsersyncer(usersyncUrl, externalUrl);
    }

    @Bean
    PulsepointUsersyncer pulsepointUsersyncer(
            @Value("${adapters.pulsepoint.usersync-url}") String usersyncUrl,
            @Value("${external-url}") String externalUrl) {
        return new PulsepointUsersyncer(usersyncUrl, externalUrl);
    }

    @Bean
    RubiconUsersyncer rubiconUsersyncer(
            @Value("${adapters.rubicon.usersync-url}") String usersyncUrl) {
        return new RubiconUsersyncer(usersyncUrl);
    }
}
