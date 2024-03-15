package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.advangelists.AdvangelistsBidder;
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
@PropertySource(value = "classpath:/bidder-config/advangelists.yaml", factory = YamlPropertySourceFactory.class)
public class AdvangelistsConfiguration {

    private static final String BIDDER_NAME = "advangelists";

    @Bean("advangelistsConfigurationProperties")
    @ConfigurationProperties("adapters.advangelists")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps advangelistsBidderDeps(BidderConfigurationProperties advangelistsConfigurationProperties,
                                      @NotBlank @Value("${external-url}") String externalUrl,
                                      JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(advangelistsConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new AdvangelistsBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}

