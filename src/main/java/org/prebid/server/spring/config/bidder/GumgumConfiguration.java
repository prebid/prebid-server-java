package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.gumgum.GumgumBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/gumgum.yaml", factory = YamlPropertySourceFactory.class)
public class GumgumConfiguration {

    private static final String BIDDER_NAME = "gumgum";

    @Bean("gumgumConfigurationProperties")
    @ConfigurationProperties("adapters.gumgum")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps gumgumBidderDeps(BidderConfigurationProperties gumgumConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(gumgumConfigurationProperties)
                .bidderCreator(config -> new GumgumBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
