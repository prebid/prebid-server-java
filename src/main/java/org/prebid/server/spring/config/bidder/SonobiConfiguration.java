package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.sonobi.SonobiBidder;
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
@PropertySource(value = "classpath:/bidder-config/sonobi.yaml", factory = YamlPropertySourceFactory.class)
public class SonobiConfiguration {

    private static final String BIDDER_NAME = "sonobi";

    @Bean("sonobiConfigurationProperties")
    @ConfigurationProperties("adapters.sonobi")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps sonobiBidderDeps(BidderConfigurationProperties sonobiConfigurationProperties,
                                CurrencyConversionService currencyConversionService,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(sonobiConfigurationProperties)
                .bidderCreator(config -> new SonobiBidder(currencyConversionService, config.getEndpoint(), mapper))
                .assemble();
    }
}
