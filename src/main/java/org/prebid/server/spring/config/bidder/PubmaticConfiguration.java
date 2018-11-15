package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.pubmatic.PubmaticAdapter;
import org.prebid.server.bidder.pubmatic.PubmaticBidder;
import org.prebid.server.bidder.pubmatic.PubmaticMetaInfo;
import org.prebid.server.bidder.pubmatic.PubmaticUsersyncer;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PubmaticConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "pubmatic";

    @Value("${adapters.pubmatic.enabled}")
    private boolean enabled;

    @Value("${adapters.pubmatic.endpoint}")
    private String endpoint;

    @Value("${adapters.pubmatic.usersync-url}")
    private String usersyncUrl;

    @Value("${adapters.pubmatic.pbs-enforces-gdpr}")
    private boolean pbsEnforcesGdpr;

    @Value("${external-url}")
    private String externalUrl;

    @Value("${adapters.pubmatic.deprecated-names}")
    private List<String> deprecatedNames;

    @Bean
    BidderDeps pubmaticBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
        return bidderDeps(httpClient, httpAdapterConnector);
    }

    @Override
    protected String bidderName() {
        return BIDDER_NAME;
    }

    @Override
    protected List<String> deprecatedNames() {
        return deprecatedNames;
    }

    @Override
    protected MetaInfo createMetaInfo() {
        return new PubmaticMetaInfo(enabled, pbsEnforcesGdpr);
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new PubmaticUsersyncer(usersyncUrl, externalUrl);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new PubmaticBidder(endpoint);
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return new PubmaticAdapter(usersyncer, endpoint);
    }

    @Override
    protected BidderRequester createBidderRequester(HttpClient httpClient, Bidder<?> bidder, Adapter<?, ?> adapter,
                                                    Usersyncer usersyncer, HttpAdapterConnector httpAdapterConnector) {
        return new HttpBidderRequester<>(bidder, httpClient);
    }
}
