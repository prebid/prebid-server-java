package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adelement.AdelementBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adelement.yaml", factory = YamlPropertySourceFactory.class)
public class AdelementConfiguration {

    private static final String BIDDER_NAME = "adelement";

    @Bean("adelementConfigurationProperties")
    @ConfigurationProperties("adapters.adelement")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adelementBidderDeps(BidderConfigurationProperties adelementConfigurationProperties,
                                   JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adelementConfigurationProperties)
                .bidderCreator(config -> new AdelementBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
