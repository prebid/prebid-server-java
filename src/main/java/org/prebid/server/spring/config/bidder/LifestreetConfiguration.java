package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.lifestreet.LifestreetAdapter;
import org.prebid.server.bidder.lifestreet.LifestreetBidder;
import org.prebid.server.bidder.lifestreet.LifestreetMetaInfo;
import org.prebid.server.bidder.lifestreet.LifestreetUsersyncer;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/lifestreet.yaml", factory = YamlPropertySourceFactory.class)
public class LifestreetConfiguration {

    private static final String BIDDER_NAME = "lifestreet";

    @Autowired
    @Qualifier("lifestreetConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("lifestreetConfigurationProperties")
    @ConfigurationProperties("adapters.lifestreet")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps lifestreetBidderDeps() {
        final Usersyncer usersyncer = new LifestreetUsersyncer(configProperties.getUsersyncUrl(), externalUrl);
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .metaInfo(new LifestreetMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr()))
                .usersyncer(usersyncer)
                .bidderCreator(() -> new LifestreetBidder(configProperties.getEndpoint()))
                .adapterCreator(() -> new LifestreetAdapter(usersyncer, configProperties.getEndpoint()))
                .assemble();
    }
}
