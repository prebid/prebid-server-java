package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.grid.GridBidder;
import org.prebid.server.bidder.grid.GridMetaInfo;
import org.prebid.server.bidder.grid.GridUsersyncer;
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
@PropertySource(value = "classpath:/bidder-config/grid.yaml", factory = YamlPropertySourceFactory.class)
public class GridConfiguration {

    private static final String BIDDER_NAME = "grid";

    @Autowired
    @Qualifier("gridConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    private String externalUrl;

    @Bean("gridConfigurationProperties")
    @ConfigurationProperties("adapters.grid")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps gridBidderDeps() {
        final Usersyncer usersyncer = new GridUsersyncer(configProperties.getUsersyncUrl(), externalUrl);
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .metaInfo(new GridMetaInfo(configProperties.getEnabled(), configProperties.getPbsEnforcesGdpr()))
                .usersyncer(usersyncer)
                .bidderCreator(() -> new GridBidder(configProperties.getEndpoint()))
                .assemble();
    }
}
