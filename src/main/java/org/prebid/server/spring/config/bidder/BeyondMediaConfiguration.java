package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.beyondmedia.BeyondMediaBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/beyondmedia.yaml", factory = YamlPropertySourceFactory.class)
public class BeyondMediaConfiguration {

    private static final String BIDDER_NAME = "beyondmedia";

    @Bean("beyondMediaConfigurationProperties")
    @ConfigurationProperties("adapters.beyondmedia")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps beyondMediaBidderDeps(BidderConfigurationProperties beyondMediaConfigurationProperties,
                                     JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(beyondMediaConfigurationProperties)
                .bidderCreator(config -> new BeyondMediaBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
