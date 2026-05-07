package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.akcelo.AkceloBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/akcelo.yaml",
        factory = YamlPropertySourceFactory.class)
public class AkceloConfiguration {

    private static final String BIDDER_NAME = "akcelo";

    @Bean("akceloConfigurationProperties")
    @ConfigurationProperties("adapters.akcelo")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps akceloBidderDeps(BidderConfigurationProperties akceloConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(akceloConfigurationProperties)
                .bidderCreator(config -> new AkceloBidder(config.getEndpoint(), mapper))
                .assemble();
    }

}
