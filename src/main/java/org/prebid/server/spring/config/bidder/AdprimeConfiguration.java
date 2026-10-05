package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adprime.AdprimeBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adprime.yaml", factory = YamlPropertySourceFactory.class)
public class AdprimeConfiguration {

    private static final String BIDDER_NAME = "adprime";

    @Bean("adprimeConfigurationProperties")
    @ConfigurationProperties("adapters.adprime")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adprimeBidderDeps(BidderConfigurationProperties adprimeConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adprimeConfigurationProperties)
                .bidderCreator(config -> new AdprimeBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
