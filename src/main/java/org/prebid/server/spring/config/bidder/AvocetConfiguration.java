package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.avocet.AvocetBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/avocet.yaml", factory = YamlPropertySourceFactory.class)
public class AvocetConfiguration {

    private static final String BIDDER_NAME = "avocet";

    @Bean("avocetConfigurationProperties")
    @ConfigurationProperties("adapters.avocet")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps avocetBidderDeps(BidderConfigurationProperties avocetConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(avocetConfigurationProperties)
                .bidderCreator(config -> new AvocetBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
