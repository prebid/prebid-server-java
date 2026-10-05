package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.teqblaze.TeqblazeBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/teqblaze.yaml", factory = YamlPropertySourceFactory.class)
public class TeqblazeConfiguration {

    private static final String BIDDER_NAME = "teqblaze";

    @Bean("teqblazeConfigurationProperties")
    @ConfigurationProperties("adapters.teqblaze")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps teqblazeBidderDeps(BidderConfigurationProperties teqblazeConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(teqblazeConfigurationProperties)
                .bidderCreator(config -> new TeqblazeBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
