package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.pubrise.PubriseBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/pubrise.yaml", factory = YamlPropertySourceFactory.class)
public class PubriseConfiguration {

    private static final String BIDDER_NAME = "pubrise";

    @Bean("pubriseConfigurationProperties")
    @ConfigurationProperties("adapters.pubrise")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps pubriseBidderDeps(BidderConfigurationProperties pubriseConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(pubriseConfigurationProperties)
                .bidderCreator(config -> new PubriseBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
