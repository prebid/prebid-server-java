package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.audiencenetwork.AudienceNetworkBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.UsersyncerCreator;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import java.util.function.Function;

@Configuration
@PropertySource(value = "classpath:/bidder-config/audiencenetwork.yaml", factory = YamlPropertySourceFactory.class)
public class AudienceNetworkConfiguration {

    private static final String BIDDER_NAME = "audienceNetwork";

    @Bean
    BidderDeps audiencenetworkBidderDeps(AudienceNetworkConfigurationProperties audienceNetworkConfigurationProperties,
                                         JacksonMapper mapper) {

        final Function<AudienceNetworkConfigurationProperties, Bidder<?>> bidderCreator =
                config -> new AudienceNetworkBidder(
                        config.getEndpoint(),
                        config.getPlatformId(),
                        config.getAppSecret(),
                        audienceNetworkConfigurationProperties.getTimeoutNotificationUrlTemplate(),
                        mapper);

        return BidderDepsAssembler.<AudienceNetworkConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(audienceNetworkConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(null))
                .bidderCreator(audienceNetworkConfigurationProperties.getEnabled() ? bidderCreator : null)
                .assemble();
    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    @Component("audienceNetworkConfigurationProperties")
    @ConfigurationProperties("adapters.audiencenetwork")
    private static class AudienceNetworkConfigurationProperties extends BidderConfigurationProperties {

        @NotNull
        private String platformId;

        @NotNull
        private String appSecret;

        @NotNull
        private String timeoutNotificationUrlTemplate;
    }
}
