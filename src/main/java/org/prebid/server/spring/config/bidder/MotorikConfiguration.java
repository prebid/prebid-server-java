package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.motorik.MotorikBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/motorik.yaml", factory = YamlPropertySourceFactory.class)
public class MotorikConfiguration {

    private static final String BIDDER_NAME = "motorik";

    @Bean("motorikConfigurationProperties")
    @ConfigurationProperties("adapters.motorik")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps motorikBidderDeps(BidderConfigurationProperties motorikConfigurationProperties,
                                    JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(motorikConfigurationProperties)
                .bidderCreator(config -> new MotorikBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
