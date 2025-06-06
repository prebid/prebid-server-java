package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.missena.MissenaBidder;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.UsersyncerCreator;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.prebid.server.version.PrebidVersionProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import jakarta.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/missena.yaml", factory = YamlPropertySourceFactory.class)
public class MissenaConfiguration {

    private static final String BIDDER_NAME = "missena";

    @Bean("missenaConfigurationProperties")
    @ConfigurationProperties("adapters.missena")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps missenaBidderDeps(BidderConfigurationProperties missenaConfigurationProperties,
                                 @NotBlank @Value("${external-url}") String externalUrl,
                                 CurrencyConversionService currencyConversionService,
                                 PrebidVersionProvider prebidVersionProvider,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(missenaConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new MissenaBidder(
                        config.getEndpoint(), mapper, currencyConversionService, prebidVersionProvider))
                .assemble();
    }
}
