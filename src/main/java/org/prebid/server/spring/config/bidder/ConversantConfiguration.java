package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.bidder.HttpAdapterRequester;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.conversant.ConversantAdapter;
import org.prebid.server.bidder.conversant.ConversantBidder;
import org.prebid.server.bidder.conversant.ConversantMetaInfo;
import org.prebid.server.bidder.conversant.ConversantUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class ConversantConfiguration {

    private static final String BIDDER_NAME = "conversant";

    @Value("${adapters.conversant.endpoint}")
    private String endpoint;

    @Value("${adapters.conversant.usersync-url}")
    private String usersyncUrl;

    @Value("${external-url}")
    private String externalUrl;

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderDeps conversantBidderDeps(HttpAdapterConnector httpAdapterConnector) {
        final Usersyncer usersyncer = new ConversantUsersyncer(usersyncUrl, externalUrl);
        final Adapter<?, ?> adapter = new ConversantAdapter(usersyncer, endpoint);
        final BidderRequester bidderRequester = new HttpAdapterRequester(BIDDER_NAME, adapter, usersyncer,
                httpAdapterConnector);

        return BidderDeps.of(BIDDER_NAME, new ConversantMetaInfo(), usersyncer, new ConversantBidder(), adapter,
                bidderRequester);
    }
}
