package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adxcg.AdxcgBidder;
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
@PropertySource(value = "classpath:/bidder-config/adxcg.yaml", factory = YamlPropertySourceFactory.class)
public class AdxcgConfiguration {

    private static final String BIDDER_NAME = "adxcg";

    @Bean("adxcgConfigurationProperties")
    @ConfigurationProperties("adapters.adxcg")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adxcgBidderDeps(BidderConfigurationProperties adxcgConfigurationProperties,
                               @NotBlank @Value("${external-url}") String externalUrl,
                               JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adxcgConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new AdxcgBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
