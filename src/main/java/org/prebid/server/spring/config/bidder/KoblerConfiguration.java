package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.kobler.KoblerBidder;
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
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/kobler.yaml", factory = YamlPropertySourceFactory.class)
public class KoblerConfiguration {

    private static final String BIDDER_NAME = "kobler";

    @Bean("koblerConfigurationProperties")
    @ConfigurationProperties("adapters.kobler")
    KoblerConfigurationProperties configurationProperties() {
        return new KoblerConfigurationProperties();
    }

    @Bean
    BidderDeps koblerBidderDeps(KoblerConfigurationProperties config,
                                CurrencyConversionService currencyConversionService,
                                @NotBlank @Value("${external-url}") String externalUrl,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.<KoblerConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(config)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(cfg -> new KoblerBidder(
                        cfg.getEndpoint(),
                        cfg.getDefaultBidCurrency(),
                        cfg.getDevEndpoint(),
                        cfg.getExtPrebid(),
                        currencyConversionService,
                        mapper))
                .assemble();

    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class KoblerConfigurationProperties extends BidderConfigurationProperties {

        @NotBlank
        private String defaultBidCurrency;

        @NotBlank
        private String devEndpoint;

        @NotBlank
        private String extPrebid;
    }
}
