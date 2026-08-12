package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.pulsepoint.PulsepointBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/pulsepoint.yaml", factory = YamlPropertySourceFactory.class)
public class PulsepointConfiguration {

    private static final String BIDDER_NAME = "pulsepoint";

    @Bean("pulsepointConfigurationProperties")
    @ConfigurationProperties("adapters.pulsepoint")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps pulsepointBidderDeps(BidderConfigurationProperties pulsepointConfigurationProperties,
                                    JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(pulsepointConfigurationProperties)
                .bidderCreator(config -> new PulsepointBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
