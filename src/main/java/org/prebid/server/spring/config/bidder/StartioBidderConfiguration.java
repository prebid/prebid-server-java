package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.startio.StartioBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/startio.yaml", factory = YamlPropertySourceFactory.class)
public class StartioBidderConfiguration {

    private static final String BIDDER_NAME = "startio";

    @Bean("startioConfigurationProperties")
    @ConfigurationProperties("adapters.startio")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps startioBidderDeps(BidderConfigurationProperties startioConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(startioConfigurationProperties)
                .bidderCreator(config -> new StartioBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
