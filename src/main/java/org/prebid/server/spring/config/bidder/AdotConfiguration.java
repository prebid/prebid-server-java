package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adot.AdotBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adot.yaml", factory = YamlPropertySourceFactory.class)
public class AdotConfiguration {

    private static final String BIDDER_NAME = "adot";

    @Bean("adotConfigurationProperties")
    @ConfigurationProperties("adapters.adot")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adotBidderDeps(BidderConfigurationProperties adotConfigurationProperties,
                              JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adotConfigurationProperties)
                .bidderCreator(config -> new AdotBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
