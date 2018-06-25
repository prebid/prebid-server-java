package org.prebid.server.spring.config.bidder;

import io.vertx.core.http.HttpClient;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.bidder.HttpAdapterRequester;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.lifestreet.LifestreetAdapter;
import org.prebid.server.bidder.lifestreet.LifestreetBidder;
import org.prebid.server.bidder.lifestreet.LifestreetMetaInfo;
import org.prebid.server.bidder.lifestreet.LifestreetUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LifestreetConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "lifestreet";

    @Value("${adapters.lifestreet.enabled}")
    private boolean enabled;

    @Value("${adapters.lifestreet.endpoint}")
    private String endpoint;

    @Value("${adapters.lifestreet.usersync-url}")
    private String usersyncUrl;

    @Value("${external-url}")
    private String externalUrl;

    @Bean
    BidderDeps lifestreetBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
        return bidderDeps(httpClient, httpAdapterConnector);
    }

    @Override
    protected String bidderName() {
        return BIDDER_NAME;
    }

    @Override
    protected MetaInfo createMetaInfo() {
        return new LifestreetMetaInfo(enabled);
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new LifestreetUsersyncer(usersyncUrl, externalUrl);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new LifestreetBidder();
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return new LifestreetAdapter(usersyncer, endpoint);
    }

    @Override
    protected BidderRequester createBidderRequester(HttpClient httpClient, Bidder<?> bidder, Adapter<?, ?> adapter,
                                                    Usersyncer usersyncer, HttpAdapterConnector httpAdapterConnector) {
        return new HttpAdapterRequester(BIDDER_NAME, adapter, usersyncer, httpAdapterConnector);
    }
}
