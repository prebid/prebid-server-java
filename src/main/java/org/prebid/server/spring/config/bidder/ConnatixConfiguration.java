package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.connatix.ConnatixBidder;
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
@PropertySource(value = "classpath:/bidder-config/connatix.yaml", factory = YamlPropertySourceFactory.class)
public class ConnatixConfiguration {

    private static final String BIDDER_NAME = "connatix";

    @Bean("connatixConfigurationProperties")
    @ConfigurationProperties("adapters.connatix")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps connatixBidderDeps(BidderConfigurationProperties connatixConfigurationProperties,
                                  JacksonMapper mapper,
                                  CurrencyConversionService currencyConversionService) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(connatixConfigurationProperties)
                .bidderCreator(config -> new ConnatixBidder(config.getEndpoint(), currencyConversionService, mapper))
                .assemble();
    }
}
