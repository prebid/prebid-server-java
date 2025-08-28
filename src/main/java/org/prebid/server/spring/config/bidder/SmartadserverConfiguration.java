package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.smartadserver.SmartadserverBidder;
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
@PropertySource(value = "classpath:/bidder-config/smartadserver.yaml", factory = YamlPropertySourceFactory.class)
public class SmartadserverConfiguration {

    private static final String BIDDER_NAME = "smartadserver";

    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class SmartadserverConfigurationProperties extends BidderConfigurationProperties {

        private String secondaryEndpoint;
    }

    @Bean("smartadserverConfigurationProperties")
    @ConfigurationProperties("adapters.smartadserver")
    SmartadserverConfigurationProperties configurationProperties() {
        return new SmartadserverConfigurationProperties();
    }

    @Bean
    BidderDeps smartadserverBidderDeps(SmartadserverConfigurationProperties smartadserverConfigurationProperties,
                                       @NotBlank @Value("${external-url}") String externalUrl,
                                       JacksonMapper mapper) {

        return BidderDepsAssembler.<SmartadserverConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(smartadserverConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new SmartadserverBidder(
                        config.getEndpoint(), config.getSecondaryEndpoint(), mapper))
                .assemble();
    }
}
