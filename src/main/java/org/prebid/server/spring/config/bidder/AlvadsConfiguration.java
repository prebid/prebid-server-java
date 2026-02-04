package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.alvads.AlvadsBidder;
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
@PropertySource(value = "classpath:/bidder-config/alvads.yaml", factory = YamlPropertySourceFactory.class)
public class AlvadsConfiguration {

    private static final String BIDDER_NAME = "alvads";

    @Bean("alvadsConfigurationProperties")
    @ConfigurationProperties("adapters.alvads")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps alvadsBidderDeps(
            BidderConfigurationProperties alvadsConfigurationProperties,
            @NotBlank @Value("${external-url}") String externalUrl,
            JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(alvadsConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new AlvadsBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
