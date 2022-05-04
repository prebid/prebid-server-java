package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.rubicon.RubiconBidder;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.floors.PriceFloorResolver;
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

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Configuration
@PropertySource(value = "classpath:/bidder-config/rubicon.yaml", factory = YamlPropertySourceFactory.class)
public class RubiconConfiguration {

    private static final String BIDDER_NAME = "rubicon";

    @Bean("rubiconConfigurationProperties")
    @ConfigurationProperties("adapters.rubicon")
    RubiconConfigurationProperties configurationProperties() {
        return new RubiconConfigurationProperties();
    }

    @Bean
    BidderDeps rubiconBidderDeps(RubiconConfigurationProperties rubiconConfigurationProperties,
                                 @NotBlank @Value("${external-url}") String externalUrl,
                                 CurrencyConversionService currencyConversionService,
                                 PriceFloorResolver floorResolver,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.<RubiconConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(rubiconConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(null))
                .bidderCreator(config ->
                        new RubiconBidder(
                                config.getEndpoint(),
                                config.getXapi().getUsername(),
                                config.getXapi().getPassword(),
                                config.getMetaInfo().getSupportedVendors(),
                                config.getGenerateBidId(),
                                currencyConversionService,
                                floorResolver,
                                mapper))
                .assemble();
    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class RubiconConfigurationProperties extends BidderConfigurationProperties {

        @Valid
        @NotNull
        private XAPI xapi = new XAPI();

        @NotNull
        private Boolean generateBidId;
    }

    @Data
    @NoArgsConstructor
    private static class XAPI {

        @NotNull
        private String username;

        @NotNull
        private String password;
    }
}
