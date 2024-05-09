package org.prebid.server.spring.config.bidder;

import jakarta.validation.constraints.NotBlank;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.loyal.LoyalBidder;
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

@Configuration
@PropertySource(value = "classpath:/bidder-config/loyal.yaml", factory = YamlPropertySourceFactory.class)
public class LoyalConfiguration {

    private static final String BIDDER_NAME = "loyal";

    @Bean("loyalConfigurationProperties")
    @ConfigurationProperties("adapters.loyal")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps loaylBidderDeps(BidderConfigurationProperties loyalConfigurationProperties,
                                 @NotBlank @Value("${external-url}") String externalUrl,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(loyalConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new LoyalBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
