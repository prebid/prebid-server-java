package org.prebid.server.spring.config.bidder;

import io.vertx.core.http.HttpClient;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.appnexus.AppnexusAdapter;
import org.prebid.server.bidder.appnexus.AppnexusBidder;
import org.prebid.server.bidder.appnexus.AppnexusMetaInfo;
import org.prebid.server.bidder.appnexus.AppnexusUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class AppnexusConfiguration {

    private static final String BIDDER_NAME = "appnexus";

    @Value("${adapters.appnexus.endpoint}")
    private String endpoint;

    @Value("${adapters.appnexus.usersync-url}")
    private String usersyncUrl;

    @Value("${external-url}")
    private String externalUrl;

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderDeps appnexusBidderDeps(HttpClient httpClient) {
        final Usersyncer usersyncer = new AppnexusUsersyncer(usersyncUrl, externalUrl);
        final Adapter adapter = new AppnexusAdapter(usersyncer, endpoint);
        final Bidder bidder = new AppnexusBidder(endpoint);
        final BidderRequester bidderRequester = new HttpBidderRequester(bidder, httpClient);

        return BidderDeps.of(BIDDER_NAME, new AppnexusMetaInfo(), usersyncer, adapter, bidderRequester);
    }
}
