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
import org.prebid.server.bidder.adtelligent.AdtelligentBidder;
import org.prebid.server.bidder.adtelligent.AdtelligentMetaInfo;
import org.prebid.server.bidder.adtelligent.AdtelligentUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdtelligentConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "adtelligent";

    @Value("${adapters.adtelligent.enabled}")
    private boolean enabled;

    @Value("${adapters.adtelligent.endpoint}")
    private String endpoint;

    @Value("${adapters.adtelligent.usersync-url}")
    private String usersyncUrl;

    @Value("${adapters.adtelligent.pbs-enforces-gdpr}")
    private boolean pbsEnforcesGdpr;

    @Value("${external-url}")
    private String externalUrl;

    @Override
    protected String bidderName() {
        return BIDDER_NAME;
    }

    @Bean
    BidderDeps adtelligentBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
        return bidderDeps(httpClient, httpAdapterConnector);
    }

    @Override
    protected MetaInfo createMetaInfo() {
        return new AdtelligentMetaInfo(enabled, pbsEnforcesGdpr);
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new AdtelligentUsersyncer(usersyncUrl, externalUrl);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new AdtelligentBidder(endpoint);
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
