package org.prebid.server.spring.config.bidder;

import io.vertx.core.http.HttpClient;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.adform.AdformAdapter;
import org.prebid.server.bidder.adform.AdformBidder;
import org.prebid.server.bidder.adform.AdformMetaInfo;
import org.prebid.server.bidder.adform.AdformUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class AdformConfiguration {

    private static final String BIDDER_NAME = "adform";

    @Value("${adapters.adform.endpoint}")
    private String endpoint;

    @Value("${adapters.adform.usersync-url}")
    private String usersyncUrl;

    @Value("${external-url}")
    private String externalUrl;

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderDeps adformBidderDeps(HttpClient httpClient) {
        final Usersyncer usersyncer = new AdformUsersyncer(usersyncUrl, externalUrl);
        final Adapter adapter = new AdformAdapter(usersyncer, endpoint);
        final Bidder bidder = new AdformBidder(endpoint);
        final BidderRequester bidderRequester = new HttpBidderRequester(bidder, httpClient);

        return BidderDeps.of(BIDDER_NAME, new AdformMetaInfo(), usersyncer, adapter, bidderRequester);
    }
}
