package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.aduptech.AduptechBidder;
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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Configuration
@PropertySource(value = "classpath:/bidder-config/aduptech.yaml", factory = YamlPropertySourceFactory.class)
public class AduptechConfiguration {

    private static final String BIDDER_NAME = "aduptech";

    @Bean("aduptechConfigurationProperties")
    @ConfigurationProperties("adapters.aduptech")
    AduptechConfigurationProperties configurationProperties() {
        return new AduptechConfigurationProperties();
    }

    @Bean
    BidderDeps aduptechBidderDeps(AduptechConfigurationProperties aduptechConfigurationProperties,
                                  @NotBlank @Value("${external-url}") String externalUrl,
                                  CurrencyConversionService currencyConversionService,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.<AduptechConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(aduptechConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new AduptechBidder(
                        config.getEndpoint(),
                        mapper,
                        currencyConversionService,
                        config.getTargetCurrency()))
                .assemble();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class AduptechConfigurationProperties extends BidderConfigurationProperties {

        @NotNull
        private String targetCurrency;
    }
}
