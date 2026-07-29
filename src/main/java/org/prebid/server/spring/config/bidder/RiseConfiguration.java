package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.rise.RiseBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/rise.yaml", factory = YamlPropertySourceFactory.class)
public class RiseConfiguration {

    private static final String BIDDER_NAME = "rise";

    @Bean("riseConfigurationProperties")
    @ConfigurationProperties("adapters.rise")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps riseBidderDeps(BidderConfigurationProperties riseConfigurationProperties,
                              JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(riseConfigurationProperties)
                .bidderCreator(config -> new RiseBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
