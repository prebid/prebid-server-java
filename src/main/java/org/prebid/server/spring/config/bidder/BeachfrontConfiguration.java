package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.beachfront.BeachfrontBidder;
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

import javax.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/beachfront.yaml", factory = YamlPropertySourceFactory.class)
public class BeachfrontConfiguration {

    private static final String BIDDER_NAME = "beachfront";

    @Bean("beachfrontConfigurationProperties")
    @ConfigurationProperties("adapters.beachfront")
    BeachfrontConfigurationProperties configurationProperties() {
        return new BeachfrontConfigurationProperties();
    }

    @Bean
    BidderDeps beachfrontBidderDeps(BeachfrontConfigurationProperties beachfrontConfigurationProperties,
                                    @NotBlank @Value("${external-url}") String externalUrl,
                                    JacksonMapper mapper) {

        return BidderDepsAssembler.<BeachfrontConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(beachfrontConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config ->
                        new BeachfrontBidder(
                                config.getEndpoint(),
                                config.getVideoEndpoint(),
                                mapper))
                .assemble();
    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class BeachfrontConfigurationProperties extends BidderConfigurationProperties {

        @NotBlank
        private String videoEndpoint;
    }
}
