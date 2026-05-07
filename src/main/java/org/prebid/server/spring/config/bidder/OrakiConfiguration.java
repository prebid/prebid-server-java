package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.oraki.OrakiBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/oraki.yaml", factory = YamlPropertySourceFactory.class)
public class OrakiConfiguration {

    private static final String BIDDER_NAME = "oraki";

    @Bean("orakiConfigurationProperties")
    @ConfigurationProperties("adapters.oraki")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps orakiBidderDeps(BidderConfigurationProperties orakiConfigurationProperties,
                               JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(orakiConfigurationProperties)
                .bidderCreator(config -> new OrakiBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
