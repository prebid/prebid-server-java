package org.prebid.server.spring.config.bidder;

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
public class SovrnConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "sovrn";

    @Autowired
    @Qualifier("sovrnConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("sovrnConfigurationProperties")
    @ConfigurationProperties("adapters.sovrn")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps sovrnBidderDeps(HttpClient httpClient, HttpAdapterConnector httpAdapterConnector) {
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
        return new SovrnMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr());
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new SovrnUsersyncer(configProperties.getUsersyncUrl(), externalUrl);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new SovrnBidder(configProperties.getEndpoint());
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return new SovrnAdapter(usersyncer, configProperties.getEndpoint());
    }

    @Override
    protected BidderRequester createBidderRequester(HttpClient httpClient, Bidder<?> bidder, Adapter<?, ?> adapter,
                                                    Usersyncer usersyncer, HttpAdapterConnector httpAdapterConnector) {
        return new HttpBidderRequester<>(bidder, httpClient);
    }
}
