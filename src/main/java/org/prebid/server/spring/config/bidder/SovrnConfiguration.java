package org.prebid.server.spring.config.bidder;

import io.vertx.core.http.HttpClient;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.sovrn.SovrnAdapter;
import org.prebid.server.bidder.sovrn.SovrnBidder;
import org.prebid.server.bidder.sovrn.SovrnMetaInfo;
import org.prebid.server.bidder.sovrn.SovrnUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class SovrnConfiguration {

    private static final String BIDDER_NAME = "sovrn";

    @Value("${adapters.sovrn.endpoint}")
    private String endpoint;

    @Value("${adapters.sovrn.usersync-url}")
    private String usersyncUrl;

    @Value("${external-url}")
    private String externalUrl;

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderDeps sovrnBidderDeps(HttpClient httpClient) {
        final Usersyncer usersyncer = new SovrnUsersyncer(usersyncUrl, externalUrl);
        final Adapter adapter = new SovrnAdapter(usersyncer, endpoint);
        final Bidder bidder = new SovrnBidder(endpoint);
        final BidderRequester bidderRequester = new HttpBidderRequester(bidder, httpClient);

        return BidderDeps.of(BIDDER_NAME, new SovrnMetaInfo(), usersyncer, adapter, bidderRequester);
    }
}
