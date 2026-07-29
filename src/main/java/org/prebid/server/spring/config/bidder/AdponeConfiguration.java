package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adpone.AdponeBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adpone.yaml", factory = YamlPropertySourceFactory.class)
public class AdponeConfiguration {

    private static final String BIDDER_NAME = "adpone";

    @Bean("adponeConfigurationProperties")
    @ConfigurationProperties("adapters.adpone")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adponeBidderDeps(BidderConfigurationProperties adponeConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adponeConfigurationProperties)
                .bidderCreator(config -> new AdponeBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
