package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.insticator.InsticatorBidder;
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
@PropertySource(value = "classpath:/bidder-config/insticator.yaml", factory = YamlPropertySourceFactory.class)
public class InsticatorConfiguration {

    private static final String BIDDER_NAME = "insticator";

    @Bean("insticatorConfigurationProperties")
    @ConfigurationProperties("adapters.insticator")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps insticatorBidderDeps(BidderConfigurationProperties insticatorConfigurationProperties,
                                    CurrencyConversionService currencyConversionService,
                                    JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(insticatorConfigurationProperties)
                .bidderCreator(config -> new InsticatorBidder(currencyConversionService, config.getEndpoint(), mapper))
                .assemble();
    }
}
