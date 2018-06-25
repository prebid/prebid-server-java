package org.prebid.server.spring.config.bidder;

import io.vertx.core.http.HttpClient;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequester;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.facebook.FacebookAdapter;
import org.prebid.server.bidder.facebook.FacebookBidder;
import org.prebid.server.bidder.facebook.FacebookMetaInfo;
import org.prebid.server.bidder.facebook.FacebookUsersyncer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FacebookConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "audienceNetwork";

    @Value("${adapters.facebook.enabled}")
    private boolean enabled;

    @Value("${adapters.facebook.endpoint}")
    private String endpoint;

    @Value("${adapters.facebook.nonSecureEndpoint}")
    private String nonSecureEndpoint;

    @Value("${adapters.facebook.usersync-url:#{null}}")
    private String usersyncUrl;

    @Value("${adapters.facebook.platformId:#{null}}")
    private String platformId;

    @Bean
    BidderDeps facebookBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
        if (enabled && (usersyncUrl == null || platformId == null)) {
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
        return new FacebookMetaInfo(enabled);
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new FacebookUsersyncer(enabled && usersyncUrl != null ? usersyncUrl : StringUtils.EMPTY);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new FacebookBidder(endpoint, nonSecureEndpoint, platformId);
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return new FacebookAdapter(usersyncer, endpoint, nonSecureEndpoint, platformId);
    }

    @Override
    protected BidderRequester createBidderRequester(HttpClient httpClient, Bidder<?> bidder, Adapter<?, ?> adapter,
                                                    Usersyncer usersyncer, HttpAdapterConnector httpAdapterConnector) {
        return new HttpBidderRequester<>(bidder, httpClient);
    }
}
