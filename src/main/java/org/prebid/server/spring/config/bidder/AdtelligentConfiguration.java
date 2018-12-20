package org.prebid.server.spring.config.bidder;

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
public class AdtelligentConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "adtelligent";

    @Autowired
    @Qualifier("adtelligentConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("adtelligentConfigurationProperties")
    @ConfigurationProperties("adapters.adtelligent")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adtelligentBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
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
        return new AdtelligentMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr());
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new AdtelligentUsersyncer(configProperties.getUsersyncUrl(), externalUrl);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new AdtelligentBidder(configProperties.getEndpoint());
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
