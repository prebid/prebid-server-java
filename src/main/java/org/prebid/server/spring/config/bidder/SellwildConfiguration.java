package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.sellwild.SellwildBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/sellwild.yaml", factory = YamlPropertySourceFactory.class)
public class SellwildConfiguration {

    private static final String BIDDER_NAME = "sellwild";

    @Bean("sellwildConfigurationProperties")
    @ConfigurationProperties("adapters.sellwild")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps sellwildBidderDeps(BidderConfigurationProperties sellwildConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(sellwildConfigurationProperties)
                .bidderCreator(config -> new SellwildBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
