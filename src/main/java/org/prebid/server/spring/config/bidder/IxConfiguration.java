package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.bidder.HttpAdapterRequester;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.ix.IxAdapter;
import org.prebid.server.bidder.ix.IxBidder;
import org.prebid.server.bidder.ix.IxMetaInfo;
import org.prebid.server.bidder.ix.IxUsersyncer;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class IxConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "ix";

    @Value("${adapters.ix.enabled}")
    private boolean enabled;

    @Value("${adapters.ix.endpoint:#{null}}")
    private String endpoint;

    @Value("${adapters.ix.usersync-url}")
    private String usersyncUrl;

    @Value("${adapters.ix.pbs-enforces-gdpr}")
    private boolean pbsEnforcesGdpr;

    @Value("${adapters.ix.deprecated-names}")
    private List<String> deprecatedNames;

    @Bean
    BidderDeps ixBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
        if (enabled && endpoint == null) {
            throw new IllegalStateException(
                    String.format("%s is enabled but has missing required configuration properties. "
                            + "Please review configuration.", BIDDER_NAME));
        }
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
        return new IxMetaInfo(enabled, pbsEnforcesGdpr);
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new IxUsersyncer(usersyncUrl);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new IxBidder();
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return new IxAdapter(usersyncer, endpoint);
    }

    @Override
    protected BidderRequester createBidderRequester(HttpClient httpClient, Bidder<?> bidder, Adapter<?, ?> adapter,
                                                    Usersyncer usersyncer, HttpAdapterConnector httpAdapterConnector) {
        return new HttpAdapterRequester(BIDDER_NAME, adapter, usersyncer, httpAdapterConnector);
    }
}
