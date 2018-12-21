package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.ttx.TtxBidder;
import org.prebid.server.bidder.ttx.TtxMetaInfo;
import org.prebid.server.bidder.ttx.TtxUsersyncer;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.List;

@Configuration
@PropertySource(value = "classpath:/bidder-config/ttx.yaml", factory = YamlPropertySourceFactory.class)
public class TtxConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "ttx";

    @Autowired
    @Qualifier("ttxConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${adapters.ttx.partner-id}")
    private String partnerId;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("ttxConfigurationProperties")
    @ConfigurationProperties("adapters.ttx")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps ttxBidderDeps() {
        return bidderDeps();
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
        return new TtxMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr());
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new TtxUsersyncer(configProperties.getUsersyncUrl(), externalUrl, partnerId);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new TtxBidder(configProperties.getEndpoint());
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return null;
    }

}
