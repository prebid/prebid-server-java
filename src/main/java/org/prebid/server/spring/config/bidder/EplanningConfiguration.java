package org.prebid.server.spring.config.bidder;

import io.vertx.core.http.HttpClient;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.eplanning.EplanningBidder;
import org.prebid.server.bidder.eplanning.EplanningMetaInfo;
import org.prebid.server.bidder.eplanning.EplanningUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EplanningConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "eplanning";

    @Value("${adapters.eplanning.enabled}")
    private boolean enabled;

    @Value("${adapters.eplanning.endpoint}")
    private String endpoint;

    @Value("${adapters.eplanning.usersync-url}")
    private String usersyncUrl;

    @Value("${adapters.eplanning.pbs-enforces-gdpr}")
    private boolean pbsEnforcesGdpr;

    @Value("${external-url}")
    private String externalUrl;

    @Override
    protected String bidderName() {
        return BIDDER_NAME;
    }

    @Bean
    BidderDeps eplanningBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
        return bidderDeps(httpClient, httpAdapterConnector);
    }

    @Override
    protected MetaInfo createMetaInfo() {
        return new EplanningMetaInfo(enabled, pbsEnforcesGdpr);
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new EplanningUsersyncer(usersyncUrl, externalUrl);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new EplanningBidder(endpoint);
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return null;
    }

    @Override
    protected BidderRequester createBidderRequester(HttpClient httpClient, Bidder<?> bidder, Adapter<?, ?> adapter,
                                                    Usersyncer usersyncer, HttpAdapterConnector httpAdapterConnector) {
        return new HttpBidderRequester<>(bidder, httpClient);
    }
}
