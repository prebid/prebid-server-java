package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.aps.ApsBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/aps.yaml", factory = YamlPropertySourceFactory.class)
public class ApsConfiguration {

    private static final String BIDDER_NAME = "aps";

    @Bean("apsConfigurationProperties")
    @ConfigurationProperties("adapters.aps")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps apsBidderDeps(BidderConfigurationProperties apsConfigurationProperties,
                             JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(apsConfigurationProperties)
                .bidderCreator(config -> new ApsBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
