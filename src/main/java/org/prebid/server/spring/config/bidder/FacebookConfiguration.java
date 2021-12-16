package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.facebook.FacebookBidder;
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

import javax.validation.constraints.NotNull;
import java.util.function.Function;

@Configuration
@PropertySource(value = "classpath:/bidder-config/facebook.yaml", factory = YamlPropertySourceFactory.class)
public class FacebookConfiguration {

    private static final String BIDDER_NAME = "audienceNetwork";

    @Bean
    BidderDeps facebookBidderDeps(FacebookConfigurationProperties facebookConfigurationProperties,
                                  JacksonMapper mapper) {

        final Function<FacebookConfigurationProperties, Bidder<?>> bidderCreator =
                config -> new FacebookBidder(
                        config.getEndpoint(),
                        config.getPlatformId(),
                        config.getAppSecret(),
                        facebookConfigurationProperties.getTimeoutNotificationUrlTemplate(),
                        mapper);

        return BidderDepsAssembler.<FacebookConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(facebookConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(null))
                .bidderCreator(facebookConfigurationProperties.getEnabled() ? bidderCreator : null)
                .assemble();
    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    @Component("facebookConfigurationProperties")
    @ConfigurationProperties("adapters.facebook")
    private static class FacebookConfigurationProperties extends BidderConfigurationProperties {

        @NotNull
        private String platformId;

        @NotNull
        private String appSecret;

        @NotNull
        private String timeoutNotificationUrlTemplate;
    }
}
