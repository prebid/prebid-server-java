package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpAdapterRequester;
import org.prebid.server.bidder.HttpConnector;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.index.IndexAdapter;
import org.prebid.server.bidder.index.IndexMetaInfo;
import org.prebid.server.bidder.index.IndexUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnProperty(name = "adapters.indexexchange.endpoint")
public class IndexConfiguration {

    private static final String BIDDER_NAME = "indexExchange";

    @Value("${adapters.indexexchange.endpoint}")
    private String endpoint;

    @Value("${adapters.indexexchange.usersync-url}")
    private String usersyncUrl;

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderDeps indexBidderDeps(HttpConnector httpConnector) {
        final Usersyncer usersyncer = new IndexUsersyncer(usersyncUrl);
        final Adapter adapter = new IndexAdapter(usersyncer, endpoint);
        final BidderRequester bidderRequester = new HttpAdapterRequester(BIDDER_NAME, adapter, usersyncer,
                httpConnector);

        return BidderDeps.of(BIDDER_NAME, new IndexMetaInfo(), usersyncer, adapter, bidderRequester);
    }
}
