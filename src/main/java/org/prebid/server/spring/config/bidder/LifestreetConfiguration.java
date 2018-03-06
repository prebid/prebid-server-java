package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.bidder.HttpAdapterRequester;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.lifestreet.LifestreetAdapter;
import org.prebid.server.bidder.lifestreet.LifestreetMetaInfo;
import org.prebid.server.bidder.lifestreet.LifestreetUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class LifestreetConfiguration {

    private static final String BIDDER_NAME = "lifestreet";

    @Value("${adapters.lifestreet.endpoint}")
    private String endpoint;

    @Value("${adapters.lifestreet.usersync-url}")
    private String usersyncUrl;

    @Value("${external-url}")
    private String externalUrl;

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderDeps lifestreetBidderDeps(HttpAdapterConnector httpAdapterConnector) {
        final Usersyncer usersyncer = new LifestreetUsersyncer(usersyncUrl, externalUrl);
        final Adapter adapter = new LifestreetAdapter(usersyncer, endpoint);
        final BidderRequester bidderRequester = new HttpAdapterRequester(BIDDER_NAME, adapter, usersyncer,
                httpAdapterConnector);

        return BidderDeps.of(BIDDER_NAME, new LifestreetMetaInfo(), usersyncer, adapter, bidderRequester);
    }
}
