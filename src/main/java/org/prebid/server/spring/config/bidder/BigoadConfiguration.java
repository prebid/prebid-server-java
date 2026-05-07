package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.bigoad.BigoadBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/bigoad.yaml", factory = YamlPropertySourceFactory.class)
public class BigoadConfiguration {

    private static final String BIDDER_NAME = "bigoad";

    @Bean("bigoadConfigurationProperties")
    @ConfigurationProperties("adapters.bigoad")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps bigoadBidderDeps(BidderConfigurationProperties bigoadConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(bigoadConfigurationProperties)
                .bidderCreator(config -> new BigoadBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
