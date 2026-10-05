package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.yieldone.YieldoneBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/yieldone.yaml", factory = YamlPropertySourceFactory.class)
public class YieldoneConfiguration {

    private static final String BIDDER_NAME = "yieldone";

    @Bean("yieldoneConfigurationProperties")
    @ConfigurationProperties("adapters.yieldone")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps yieldoneBidderDeps(BidderConfigurationProperties yieldoneConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(yieldoneConfigurationProperties)
                .bidderCreator(config -> new YieldoneBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
