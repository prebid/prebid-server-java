package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.thirtythreeacross.ThirtyThreeAcrossBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/thirtythreeacross.yaml", factory = YamlPropertySourceFactory.class)
public class ThirtyThreeAcrossConfiguration {

    private static final String BIDDER_NAME = "thirtythreeacross";

    @Bean("thirtyThreeAcrossConfigurationProperties")
    @ConfigurationProperties("adapters.thirtythreeacross")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps thirtythreeacrossBidderDeps(BidderConfigurationProperties thirtyThreeAcrossConfigurationProperties,
                                           JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(thirtyThreeAcrossConfigurationProperties)
                .bidderCreator(config -> new ThirtyThreeAcrossBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
