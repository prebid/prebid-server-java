package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adtonos.AdtonosBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adtonos.yaml", factory = YamlPropertySourceFactory.class)
public class AdtonosConfiguration {

    private static final String BIDDER_NAME = "adtonos";

    @Bean("adtonosConfigurationProperties")
    @ConfigurationProperties("adapters.adtonos")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adtonosBidderDeps(BidderConfigurationProperties adtonosConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adtonosConfigurationProperties)
                .bidderCreator(config -> new AdtonosBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
