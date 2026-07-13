package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.seedtag.SeedtagBidder;
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
@PropertySource(value = "classpath:/bidder-config/seedtag.yaml", factory = YamlPropertySourceFactory.class)
public class SeedtagConfiguration {

    private static final String BIDDER_NAME = "seedtag";

    @Bean("seedtagConfigurationProperties")
    @ConfigurationProperties("adapters.seedtag")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps seedtagBidderDeps(BidderConfigurationProperties seedtagConfigurationProperties,
                                 CurrencyConversionService currencyConversionService,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(seedtagConfigurationProperties)
                .bidderCreator(config -> new SeedtagBidder(
                        config.getEndpoint(),
                        currencyConversionService,
                        mapper))
                .assemble();
    }
}
