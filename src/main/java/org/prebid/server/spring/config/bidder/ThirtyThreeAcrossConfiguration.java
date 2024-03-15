package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.thirtythreeacross.ThirtyThreeAcrossBidder;
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
@PropertySource(value = "classpath:/bidder-config/thirtythreeacross.yaml", factory = YamlPropertySourceFactory.class)
public class ThirtyThreeAcrossConfiguration {

    private static final String BIDDER_NAME = "thirtythreeacross";

    @Bean("thirtyThreeAcrossConfigurationProperties")
    @ConfigurationProperties("adapters.thirtythreeacross")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps thirtythreeacrossBidderDeps(BidderConfigurationProperties thirtyThreeAcrossConfigurationProperties,
                                           @NotBlank @Value("${external-url}") String externalUrl,
                                           JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(thirtyThreeAcrossConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new ThirtyThreeAcrossBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
