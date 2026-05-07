package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.lmkiviads.LmKiviAdsBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/lmkiviads.yaml", factory = YamlPropertySourceFactory.class)
public class LmKiviAdsBidderConfiguration {

    private static final String BIDDER_NAME = "lmkiviads";

    @Bean("lmkiviadsConfigurationProperties")
    @ConfigurationProperties("adapters.lmkiviads")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps lmKiviadsBidderDeps(BidderConfigurationProperties lmkiviadsConfigurationProperties,
                                   JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(lmkiviadsConfigurationProperties)
                .bidderCreator(config -> new LmKiviAdsBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
