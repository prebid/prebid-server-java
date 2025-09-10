package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.nexx360.Nexx360Bidder;
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
@PropertySource(value = "classpath:/bidder-config/nexx360.yaml", factory = YamlPropertySourceFactory.class)
public class Nexx360Configuration {

    private static final String BIDDER_NAME = "nexx360";

    @Bean("nexx360ConfigurationProperties")
    @ConfigurationProperties("adapters.nexx360")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps nexx360BidderDeps(BidderConfigurationProperties nexx360ConfigurationProperties,
                                 @NotBlank @Value("${external-url}") String externalUrl,
                                 PrebidVersionProvider prebidVersionProvider,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(nexx360ConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new Nexx360Bidder(
                        config.getEndpoint(),
                        mapper,
                        prebidVersionProvider))
                .assemble();
    }
}
