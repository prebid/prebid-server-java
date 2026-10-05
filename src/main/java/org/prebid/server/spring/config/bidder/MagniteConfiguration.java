package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.magnite.MagniteBidder;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.floors.PriceFloorResolver;
import org.prebid.server.identity.UUIDIdGenerator;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.prebid.server.version.PrebidVersionProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Configuration
@PropertySource(value = "classpath:/bidder-config/magnite.yaml", factory = YamlPropertySourceFactory.class)
public class MagniteConfiguration {

    private static final String BIDDER_NAME = "magnite";

    @Bean("magniteConfigurationProperties")
    @ConfigurationProperties("adapters.magnite")
    MagniteConfigurationProperties configurationProperties() {
        return new MagniteConfigurationProperties();
    }

    @Bean
    BidderDeps magniteBidderDeps(MagniteConfigurationProperties magniteConfigurationProperties,
                                 @NotBlank @Value("${external-url}") String externalUrl,
                                 CurrencyConversionService currencyConversionService,
                                 PriceFloorResolver floorResolver,
                                 PrebidVersionProvider versionProvider,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.<MagniteConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(magniteConfigurationProperties)
                .bidderCreator(config ->
                        new MagniteBidder(
                                BIDDER_NAME,
                                config.getEndpoint(),
                                config.getDefaultTkXInt(),
                                externalUrl,
                                config.getXapi().getUsername(),
                                config.getXapi().getPassword(),
                                config.getMetaInfo().getSupportedVendors(),
                                config.getGenerateBidId() == null || config.getGenerateBidId(),
                                config.getApexRendererUrl(),
                                currencyConversionService,
                                floorResolver,
                                versionProvider,
                                new UUIDIdGenerator(),
                                mapper))
                .assemble();
    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class MagniteConfigurationProperties extends BidderConfigurationProperties {

        @Valid
        @NotNull
        private XAPI xapi = new XAPI();

        private Boolean generateBidId;

        @NotNull
        private String apexRendererUrl;

        private String defaultTkXInt;
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
