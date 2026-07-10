package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adhese.AdheseBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adhese.yaml", factory = YamlPropertySourceFactory.class)
public class AdheseConfiguration {

    private static final String BIDDER_NAME = "adhese";

    @Bean("adheseConfigurationProperties")
    @ConfigurationProperties("adapters.adhese")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adheseBidderDeps(BidderConfigurationProperties adheseConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adheseConfigurationProperties)
                .bidderCreator(config -> new AdheseBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
