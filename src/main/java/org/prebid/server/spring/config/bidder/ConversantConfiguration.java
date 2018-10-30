package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.bidder.HttpAdapterRequester;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.conversant.ConversantAdapter;
import org.prebid.server.bidder.conversant.ConversantBidder;
import org.prebid.server.bidder.conversant.ConversantMetaInfo;
import org.prebid.server.bidder.conversant.ConversantUsersyncer;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ConversantConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "conversant";

    @Value("${adapters.conversant.enabled}")
    private boolean enabled;

    @Value("${adapters.conversant.endpoint}")
    private String endpoint;

    @Value("${adapters.conversant.usersync-url}")
    private String usersyncUrl;

    @Value("${adapters.conversant.pbs-enforces-gdpr}")
    private boolean pbsEnforcesGdpr;

    @Value("${external-url}")
    private String externalUrl;

    @Value("${adapters.conversant.deprecated-names}")
    private List<String> deprecatedNames;

    @Bean
    BidderDeps conversantBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
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
        return new ConversantMetaInfo(enabled, pbsEnforcesGdpr);
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new ConversantUsersyncer(usersyncUrl, externalUrl);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new ConversantBidder();
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return new ConversantAdapter(usersyncer, endpoint);
    }

    @Override
    protected BidderRequester createBidderRequester(HttpClient httpClient, Bidder<?> bidder, Adapter<?, ?> adapter,
                                                    Usersyncer usersyncer, HttpAdapterConnector httpAdapterConnector) {
        return new HttpAdapterRequester(BIDDER_NAME, adapter, usersyncer, httpAdapterConnector);
    }
}
