package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.smaato.SmaatoBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.time.Clock;

@Configuration
@PropertySource(value = "classpath:/bidder-config/smaato.yaml", factory = YamlPropertySourceFactory.class)
public class SmaatoConfiguration {

    private static final String BIDDER_NAME = "smaato";

    @Bean("smaatoConfigurationProperties")
    @ConfigurationProperties("adapters.smaato")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps smaatoBidderDeps(BidderConfigurationProperties smaatoConfigurationProperties,
                                Clock clock,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(smaatoConfigurationProperties)
                .bidderCreator(config -> new SmaatoBidder(config.getEndpoint(), mapper, clock))
                .assemble();
    }
}
