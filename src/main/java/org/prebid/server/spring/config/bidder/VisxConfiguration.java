package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.visx.VisxBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/visx.yaml", factory = YamlPropertySourceFactory.class)
public class VisxConfiguration {

    private static final String BIDDER_NAME = "visx";

    @Bean("visxConfigurationProperties")
    @ConfigurationProperties("adapters.visx")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps visxBidderDeps(BidderConfigurationProperties visxConfigurationProperties,
                              JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(visxConfigurationProperties)
                .bidderCreator(config -> new VisxBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
