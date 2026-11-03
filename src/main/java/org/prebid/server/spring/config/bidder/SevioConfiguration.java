package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.sevio.SevioBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/sevio.yaml", factory = YamlPropertySourceFactory.class)
public class SevioConfiguration {

    private static final String BIDDER_NAME = "sevio";

    @Bean("sevioConfigurationProperties")
    @ConfigurationProperties("adapters.sevio")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps sevioBidderDeps(BidderConfigurationProperties sevioConfigurationProperties, JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(sevioConfigurationProperties)
                .bidderCreator(config -> new SevioBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
