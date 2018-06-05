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
import org.prebid.server.bidder.index.IndexAdapter;
import org.prebid.server.bidder.index.IndexBidder;
import org.prebid.server.bidder.index.IndexMetaInfo;
import org.prebid.server.bidder.index.IndexUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class IndexConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "indexExchange";

    @Value("${adapters.indexexchange.enabled}")
    private boolean enabled;

    @Value("${adapters.indexexchange.endpoint:#{null}}")
    private String endpoint;

    @Value("${adapters.indexexchange.usersync-url}")
    private String usersyncUrl;

    @Value("${adapters.indexexchange.pbs-enforces-gdpr}")
    private boolean pbsEnforcesGdpr;

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    BidderDeps indexexchangeBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
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
    protected MetaInfo createMetaInfo() {
        return new IndexMetaInfo(enabled);
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new IndexUsersyncer(usersyncUrl, pbsEnforcesGdpr);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new IndexBidder();
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return new IndexAdapter(usersyncer, endpoint);
    }

    @Override
    protected BidderRequester createBidderRequester(HttpClient httpClient, Bidder<?> bidder, Adapter<?, ?> adapter,
                                                    Usersyncer usersyncer, HttpAdapterConnector httpAdapterConnector) {
        return new HttpAdapterRequester(BIDDER_NAME, adapter, usersyncer, httpAdapterConnector);
    }
}
