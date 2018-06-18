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
import org.prebid.server.bidder.sovrn.SovrnAdapter;
import org.prebid.server.bidder.sovrn.SovrnBidder;
import org.prebid.server.bidder.sovrn.SovrnMetaInfo;
import org.prebid.server.bidder.sovrn.SovrnUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SovrnConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "sovrn";

    @Value("${adapters.sovrn.enabled}")
    private boolean enabled;

    @Value("${adapters.sovrn.endpoint}")
    private String endpoint;

    @Value("${adapters.sovrn.usersync-url}")
    private String usersyncUrl;

    @Value("${adapters.sovrn.pbs-enforces-gdpr}")
    private boolean pbsEnforcesGdpr;

    @Value("${external-url}")
    private String externalUrl;

    @Bean
    BidderDeps sovrnBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
        return bidderDeps(httpClient, httpAdapterConnector);
    }

    @Override
    protected String bidderName() {
        return BIDDER_NAME;
    }

    @Override
    protected MetaInfo createMetaInfo() {
        return new SovrnMetaInfo(enabled);
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new SovrnUsersyncer(usersyncUrl, externalUrl, pbsEnforcesGdpr);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new SovrnBidder(endpoint);
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return new SovrnAdapter(usersyncer, endpoint);
    }

    @Override
    protected BidderRequester createBidderRequester(HttpClient httpClient, Bidder<?> bidder, Adapter<?, ?> adapter,
                                                    Usersyncer usersyncer, HttpAdapterConnector httpAdapterConnector) {
        return new HttpBidderRequester<>(bidder, httpClient);
    }
}
