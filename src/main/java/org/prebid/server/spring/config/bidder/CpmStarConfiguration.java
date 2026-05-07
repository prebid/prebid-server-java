package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.cpmstar.CpmStarBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/cpmstar.yaml", factory = YamlPropertySourceFactory.class)
public class CpmStarConfiguration {

    private static final String BIDDER_NAME = "cpmstar";

    @Bean("cpmstarConfigurationProperties")
    @ConfigurationProperties("adapters.cpmstar")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps cpmstarBidderDeps(BidderConfigurationProperties cpmstarConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(cpmstarConfigurationProperties)
                .bidderCreator(config -> new CpmStarBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
