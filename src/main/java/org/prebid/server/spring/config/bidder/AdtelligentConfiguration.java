package org.prebid.server.spring.config.bidder;

import io.vertx.core.http.HttpClient;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.adtelligent.AdtelligentBidder;
import org.prebid.server.bidder.adtelligent.AdtelligentMetaInfo;
import org.prebid.server.bidder.adtelligent.AdtelligentUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class AdtelligentConfiguration {

    private static final String BIDDER_NAME = "adtelligent";

    @Value("${adapters.adtelligent.endpoint}")
    private String endpoint;

    @Value("${adapters.adtelligent.usersync-url}")
    private String usersyncUrl;

    @Value("${external-url}")
    private String externalUrl;

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderDeps adtelligentBidderDeps(HttpClient httpClient) {
        final Usersyncer usersyncer = new AdtelligentUsersyncer(usersyncUrl, externalUrl);
        final Bidder<?> bidder = new AdtelligentBidder(endpoint);
        final BidderRequester bidderRequester = new HttpBidderRequester<>(bidder, httpClient);
        return BidderDeps.of(BIDDER_NAME, new AdtelligentMetaInfo(), usersyncer, bidder, null, bidderRequester);
    }
}
