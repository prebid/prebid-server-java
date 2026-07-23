package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.outbrain.OutbrainBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/outbrain.yaml", factory = YamlPropertySourceFactory.class)
public class OutbrainConfiguration {

    private static final String BIDDER_NAME = "outbrain";

    @Bean("outbrainConfigurationProperties")
    @ConfigurationProperties("adapters.outbrain")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps outbrainBidderDeps(BidderConfigurationProperties outbrainConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(outbrainConfigurationProperties)
                .bidderCreator(config -> new OutbrainBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
