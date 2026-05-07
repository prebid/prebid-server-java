package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.bwx.BwxBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/bwx.yaml", factory = YamlPropertySourceFactory.class)
public class BwxConfiguration {

    private static final String BIDDER_NAME = "bwx";

    @Bean("bwxConfigurationProperties")
    @ConfigurationProperties("adapters.bwx")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps bwxBidderDeps(BidderConfigurationProperties bwxConfigurationProperties,
                             JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(bwxConfigurationProperties)
                .bidderCreator(config -> new BwxBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
