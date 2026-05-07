package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.richaudience.RichaudienceBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/richaudience.yaml", factory = YamlPropertySourceFactory.class)
public class RichaudienceConfiguration {

    private static final String BIDDER_NAME = "richaudience";

    @Bean("richaudienceConfigurationProperties")
    @ConfigurationProperties("adapters.richaudience")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps richaudienceBidderDeps(BidderConfigurationProperties richaudienceConfigurationProperties,
                                      JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(richaudienceConfigurationProperties)
                .bidderCreator(config -> new RichaudienceBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
