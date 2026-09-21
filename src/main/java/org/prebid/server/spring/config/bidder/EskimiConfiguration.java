package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.eskimi.EskimiBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/eskimi.yaml", factory = YamlPropertySourceFactory.class)
public class EskimiConfiguration {

    private static final String BIDDER_NAME = "eskimi";

    @Bean("eskimiConfigurationProperties")
    @ConfigurationProperties("adapters.eskimi")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps eskimiBidderDeps(BidderConfigurationProperties eskimiConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(eskimiConfigurationProperties)
                .bidderCreator(config -> new EskimiBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
