package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.orbidder.OrbidderBidder;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/orbidder.yaml", factory = YamlPropertySourceFactory.class)
public class OrbidderConfiguration {

    private static final String BIDDER_NAME = "orbidder";

    @Bean("orbidderConfigurationProperties")
    @ConfigurationProperties("adapters.orbidder")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps orbidderBidderDeps(BidderConfigurationProperties orbidderConfigurationProperties,
                                  CurrencyConversionService currencyConversionService,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(orbidderConfigurationProperties)
                .bidderCreator(config -> new OrbidderBidder(config.getEndpoint(), currencyConversionService, mapper))
                .assemble();
    }
}
