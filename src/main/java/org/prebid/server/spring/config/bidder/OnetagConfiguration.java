package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.onetag.OnetagBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/onetag.yaml", factory = YamlPropertySourceFactory.class)
public class OnetagConfiguration {

    private static final String BIDDER_NAME = "onetag";

    @Bean("onetagConfigurationProperties")
    @ConfigurationProperties("adapters.onetag")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps onetagBidderDeps(BidderConfigurationProperties onetagConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(onetagConfigurationProperties)
                .bidderCreator(config -> new OnetagBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
