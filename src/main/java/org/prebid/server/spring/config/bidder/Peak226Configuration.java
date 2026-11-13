package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.peak226.Peak226Bidder;
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
@PropertySource(value = "classpath:/bidder-config/peak226.yaml", factory = YamlPropertySourceFactory.class)
public class Peak226Configuration {

    private static final String BIDDER_NAME = "peak226";

    @Bean("peak226ConfigurationProperties")
    @ConfigurationProperties("adapters.peak226")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps peak226BidderDeps(BidderConfigurationProperties peak226ConfigurationProperties,
                                 CurrencyConversionService currencyConversionService,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(peak226ConfigurationProperties)
                .bidderCreator(config -> new Peak226Bidder(
                        config.getEndpoint(),
                        currencyConversionService,
                        mapper))
                .assemble();
    }
}
