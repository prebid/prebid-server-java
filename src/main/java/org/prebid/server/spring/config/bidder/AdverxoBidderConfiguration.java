package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adverxo.AdverxoBidder;
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
@PropertySource(value = "classpath:/bidder-config/adverxo.yaml", factory = YamlPropertySourceFactory.class)
public class AdverxoBidderConfiguration {

    private static final String BIDDER_NAME = "adverxo";

    @Bean("adverxoConfigurationProperties")
    @ConfigurationProperties("adapters.adverxo")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adverxoBidderDeps(BidderConfigurationProperties adverxoConfigurationProperties,
                                 JacksonMapper mapper,
                                 CurrencyConversionService currencyConversionService) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adverxoConfigurationProperties)
                .bidderCreator(config -> new AdverxoBidder(config.getEndpoint(), mapper, currencyConversionService))
                .assemble();
    }
}
