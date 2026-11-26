package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.clickio.ClickioBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/clickio.yaml", factory = YamlPropertySourceFactory.class)
public class ClickioConfiguration {

    private static final String BIDDER_NAME = "clickio";

    @Bean("clickioConfigurationProperties")
    @ConfigurationProperties("adapters.clickio")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps clickioBidderDeps(BidderConfigurationProperties clickioConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(clickioConfigurationProperties)
                .bidderCreator(config -> new ClickioBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
