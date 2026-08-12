package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.flatads.FlatadsBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/flatads.yaml", factory = YamlPropertySourceFactory.class)
public class FlatadsConfiguration {

    private static final String BIDDER_NAME = "flatads";

    @Bean("flatadsConfigurationProperties")
    @ConfigurationProperties("adapters.flatads")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps flatadsBidderDeps(BidderConfigurationProperties flatadsConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(flatadsConfigurationProperties)
                .bidderCreator(config -> new FlatadsBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
