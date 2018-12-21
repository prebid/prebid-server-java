package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.rhythmone.RhythmoneBidder;
import org.prebid.server.bidder.rhythmone.RhythmoneMetaInfo;
import org.prebid.server.bidder.rhythmone.RhythmoneUsersyncer;
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
@PropertySource(value = "classpath:/bidder-config/rhythmone.yaml", factory = YamlPropertySourceFactory.class)
public class RhythmoneConfiguration extends BidderConfiguration {

    private static final String BIDDER_NAME = "rhythmone";

    @Autowired
    @Qualifier("rhythmoneConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("rhythmoneConfigurationProperties")
    @ConfigurationProperties("adapters.rhythmone")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps rhythmOneBidderDeps() {
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
        return new RhythmoneMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr());
    }

    @Override
    protected Usersyncer createUsersyncer() {
        return new RhythmoneUsersyncer(configProperties.getUsersyncUrl(), externalUrl);
    }

    @Override
    protected Bidder<?> createBidder(MetaInfo metaInfo) {
        return new RhythmoneBidder(configProperties.getEndpoint());
    }

    @Override
    protected Adapter<?, ?> createAdapter(Usersyncer usersyncer) {
        return null;
    }

}
