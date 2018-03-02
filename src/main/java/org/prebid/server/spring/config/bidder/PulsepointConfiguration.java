package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpAdapterRequester;
import org.prebid.server.bidder.HttpConnector;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.pulsepoint.PulsepointAdapter;
import org.prebid.server.bidder.pulsepoint.PulsepointUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class PulsepointConfiguration {

    private static final String BIDDER_NAME = "pulsepoint";

    @Value("${adapters.pulsepoint.endpoint}")
    private String endpoint;

    @Value("${adapters.pulsepoint.usersync-url}")
    private String usersyncUrl;

    @Value("${external-url}")
    private String externalUrl;

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderDeps pulsepointBidderDeps(HttpConnector httpConnector) {
        final Usersyncer usersyncer = new PulsepointUsersyncer(usersyncUrl, externalUrl);
        final Adapter adapter = new PulsepointAdapter(usersyncer, endpoint);
        final BidderRequester bidderRequester = new HttpAdapterRequester(BIDDER_NAME, adapter, usersyncer,
                httpConnector);

        return BidderDeps.of(BIDDER_NAME, usersyncer, adapter, bidderRequester);
    }
}
