package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.epsilon.EpsilonBidder;
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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Configuration
@PropertySource(value = "classpath:/bidder-config/epsilon.yaml", factory = YamlPropertySourceFactory.class)
public class EpsilonConfiguration {

    private static final String BIDDER_NAME = "epsilon";

    @Bean("epsilonConfigurationProperties")
    @ConfigurationProperties("adapters.epsilon")
    EpsilonConfigurationProperties configurationProperties() {
        return new EpsilonConfigurationProperties();
    }

    @Bean
    BidderDeps epsilonBidderDeps(EpsilonConfigurationProperties epsilonConfigurationProperties,
                                    @NotBlank @Value("${external-url}") String externalUrl,
                                    JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(epsilonConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config ->
                        new EpsilonBidder(
                                config.getEndpoint(),
                                epsilonConfigurationProperties.getGenerateBidId(),
                                mapper))
                .assemble();
    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class EpsilonConfigurationProperties extends BidderConfigurationProperties {

        @NotNull
        private Boolean generateBidId;
    }
}
