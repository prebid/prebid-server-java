package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.consumable.ConsumableBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/consumable.yaml", factory = YamlPropertySourceFactory.class)
public class ConsumableConfiguration {

    private static final String BIDDER_NAME = "consumable";

    @Bean("consumableConfigurationProperties")
    @ConfigurationProperties("adapters.consumable")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps consumableBidderDeps(BidderConfigurationProperties consumableConfigurationProperties,
                                    JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(consumableConfigurationProperties)
                .bidderCreator(config -> new ConsumableBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
