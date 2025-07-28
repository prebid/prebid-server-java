package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.rediads.RediadsBidder;
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
@PropertySource(value = "classpath:/bidder-config/rediads.yaml", factory = YamlPropertySourceFactory.class)
public class RediadsConfiguration {

    private static final String BIDDER_NAME = "rediads";

    @Bean("rediadsConfigurationProperties")
    @ConfigurationProperties("adapters.rediads")
    RediadsConfigurationProperties configurationProperties() {
        return new RediadsConfigurationProperties();
    }

    @Bean
    BidderDeps rediadsBidderDeps(RediadsConfigurationProperties rediadsConfigurationProperties,
                                 @NotBlank @Value("${external-url}") String externalUrl,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.<RediadsConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(rediadsConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new RediadsBidder(
                        config.getEndpoint(),
                        mapper,
                        config.getDefaultSubdomain()))
                .assemble();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class RediadsConfigurationProperties extends BidderConfigurationProperties {

        @NotBlank
        private String defaultSubdomain;
    }
}
