package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adview.AdviewBidder;
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
@PropertySource(value = "classpath:/bidder-config/adview.yaml", factory = YamlPropertySourceFactory.class)
public class AdviewConfiguration {

    private static final String BIDDER_NAME = "adview";

    @Bean("adviewConfigurationProperties")
    @ConfigurationProperties("adapters.adview")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adviewBidderDeps(BidderConfigurationProperties adviewConfigurationProperties,
                                JacksonMapper mapper,
                                CurrencyConversionService currencyConversionService) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adviewConfigurationProperties)
                .bidderCreator(config -> new AdviewBidder(config.getEndpoint(), currencyConversionService, mapper))
                .assemble();
    }
}
