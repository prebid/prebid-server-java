package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.optidigital.OptidigitalBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/optidigital.yaml", factory = YamlPropertySourceFactory.class)
public class OptidigitalConfiguration {

    private static final String BIDDER_NAME = "optidigital";

    @Bean("optidigitalConfigurationProperties")
    @ConfigurationProperties("adapters.optidigital")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps optidigitalBidderDeps(BidderConfigurationProperties optidigitalConfigurationProperties,
                                     JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(optidigitalConfigurationProperties)
                .bidderCreator(config -> new OptidigitalBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
