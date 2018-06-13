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
import org.prebid.server.bidder.appnexus.AppnexusAdapter;
import org.prebid.server.bidder.appnexus.AppnexusBidder;
import org.prebid.server.bidder.appnexus.AppnexusMetaInfo;
import org.prebid.server.bidder.appnexus.AppnexusUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class AppnexusConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "appnexus";

    @Value("${adapters.appnexus.enabled}")
    private boolean enabled;

    @Value("${adapters.appnexus.endpoint}")
    private String endpoint;

    @Value("${adapters.appnexus.usersync-url}")
    private String usersyncUrl;

    @Value("${adapters.appnexus.pbs-enforces-gdpr}")
    private boolean pbsEnforcesGdpr;

    @Value("${external-url}")
    private String externalUrl;

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderDeps appnexusBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
        return bidderDeps(httpClient, httpAdapterConnector);
    }

    @Override
    protected String bidderName() {
        return BIDDER_NAME;
    }

    @Override
    protected MetaInfo createMetaInfo() {
        return new AppnexusMetaInfo(enabled);
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new AppnexusUsersyncer(usersyncUrl, externalUrl, pbsEnforcesGdpr);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new AppnexusBidder(endpoint);
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return new AppnexusAdapter(usersyncer, endpoint);
    }

    @Override
    protected BidderRequester createBidderRequester(HttpClient httpClient, Bidder<?> bidder, Adapter<?, ?> adapter,
                                                    Usersyncer usersyncer, HttpAdapterConnector httpAdapterConnector) {
        return new HttpBidderRequester<>(bidder, httpClient);
    }
}
