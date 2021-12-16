package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.pubnative.PubnativeBidder;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.UsersyncerCreator;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import javax.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/pubnative.yaml", factory = YamlPropertySourceFactory.class)
public class PubnativeConfiguration {

    private static final String BIDDER_NAME = "pubnative";

    @Bean("pubnativeConfigurationProperties")
    @ConfigurationProperties("adapters.pubnative")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps pubnativeBidderDeps(BidderConfigurationProperties pubnativeConfigurationProperties,
                                   @NotBlank @Value("${external-url}") String externalUrl,
                                   CurrencyConversionService currencyConversionService,
                                   JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(pubnativeConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new PubnativeBidder(config.getEndpoint(), mapper, currencyConversionService))
                .assemble();
    }
}
