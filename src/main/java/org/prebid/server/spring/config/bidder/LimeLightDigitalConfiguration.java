package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.limelightdigital.LimeLightDigitalBidder;
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

@Configuration
@PropertySource(value = "classpath:/bidder-config/limelightDigital.yaml", factory = YamlPropertySourceFactory.class)
public class LimeLightDigitalConfiguration {

    private static final String BIDDER_NAME = "limelightDigital";

    @Bean("limelightDigitalConfigurationProperties")
    @ConfigurationProperties("adapters.limelightdigital")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps limeLightDigitalBidderDeps(BidderConfigurationProperties limelightDigitalConfigurationProperties,
                                          @NotBlank @Value("${external-url}") String externalUrl,
                                          CurrencyConversionService currencyConversionService,
                                          JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(limelightDigitalConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config ->
                        new LimeLightDigitalBidder(config.getEndpoint(), currencyConversionService, mapper))
                .assemble();
    }
}
