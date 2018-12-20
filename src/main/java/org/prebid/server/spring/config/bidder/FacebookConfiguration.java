package org.prebid.server.spring.config.bidder;

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
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class FacebookConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "audienceNetwork";

    @Autowired
    @Qualifier("facebookConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${adapters.facebook.nonSecureEndpoint}")
    private String nonSecureEndpoint;

    @Value("${adapters.facebook.platformId:#{null}}")
    private String platformId;

    @Bean("facebookConfigurationProperties")
    @ConfigurationProperties("adapters.facebook")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps facebookBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
        if (configProperties.getEnabled() && (configProperties.getUsersyncUrl() == null || platformId == null)) {
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
        return configProperties.getDeprecatedNames();
    }

    @Override
    protected List<String> aliases() {
        return configProperties.getAliases();
    }

    @Override
    protected MetaInfo createMetaInfo() {
        return new FacebookMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr());
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new FacebookUsersyncer(configProperties.getEnabled() && configProperties.getUsersyncUrl() != null
                ? configProperties.getUsersyncUrl() : StringUtils.EMPTY);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new FacebookBidder(configProperties.getEndpoint(), nonSecureEndpoint, platformId);
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return new FacebookAdapter(usersyncer, configProperties.getEndpoint(), nonSecureEndpoint, platformId);
    }

    @Override
    protected BidderRequester createBidderRequester(HttpClient httpClient, Bidder<?> bidder, Adapter<?, ?> adapter,
                                                    Usersyncer usersyncer, HttpAdapterConnector httpAdapterConnector) {
        return new HttpBidderRequester<>(bidder, httpClient);
    }
}
