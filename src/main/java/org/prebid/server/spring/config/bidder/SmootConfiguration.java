package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.smoot.SmootBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/smoot.yaml", factory = YamlPropertySourceFactory.class)
public class SmootConfiguration {

    private static final String BIDDER_NAME = "smoot";

    @Bean("smootConfigurationProperties")
    @ConfigurationProperties("adapters.smoot")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps smootBidderDeps(BidderConfigurationProperties smootConfigurationProperties,
                               JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(smootConfigurationProperties)
                .bidderCreator(config -> new SmootBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
