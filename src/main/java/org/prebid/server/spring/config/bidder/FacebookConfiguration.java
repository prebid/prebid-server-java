package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpAdapterRequester;
import org.prebid.server.bidder.HttpConnector;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.facebook.FacebookAdapter;
import org.prebid.server.bidder.facebook.FacebookMetaInfo;
import org.prebid.server.bidder.facebook.FacebookUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnProperty(name = {"adapters.facebook.usersync-url", "adapters.facebook.platformId"})
public class FacebookConfiguration {

    private static final String BIDDER_NAME = "audienceNetwork";

    @Value("${adapters.facebook.endpoint}")
    private String endpoint;

    @Value("${adapters.facebook.nonSecureEndpoint}")
    private String nonSecureEndpoint;

    @Value("${adapters.facebook.usersync-url}")
    private String usersyncUrl;

    @Value("${adapters.facebook.platformId}")
    private String platformId;

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderDeps facebookBidderDeps(HttpConnector httpConnector) {
        final Usersyncer usersyncer = new FacebookUsersyncer(usersyncUrl);
        final Adapter adapter = new FacebookAdapter(usersyncer, endpoint, nonSecureEndpoint, platformId);
        final BidderRequester bidderRequester = new HttpAdapterRequester(BIDDER_NAME, adapter, usersyncer,
                httpConnector);

        return BidderDeps.of(BIDDER_NAME, new FacebookMetaInfo(), usersyncer, adapter, bidderRequester);
    }
}
