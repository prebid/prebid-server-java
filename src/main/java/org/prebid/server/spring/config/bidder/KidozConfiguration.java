package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.kidoz.KidozBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/kidoz.yaml", factory = YamlPropertySourceFactory.class)
public class KidozConfiguration {

    private static final String BIDDER_NAME = "kidoz";

    @Bean("kidozConfigurationProperties")
    @ConfigurationProperties("adapters.kidoz")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps kidozBidderDeps(BidderConfigurationProperties kidozConfigurationProperties,
                               JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(kidozConfigurationProperties)
                .bidderCreator(config -> new KidozBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
