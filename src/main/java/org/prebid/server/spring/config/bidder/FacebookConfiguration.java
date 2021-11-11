package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.facebook.FacebookBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.UsersyncerCreator;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Configuration
@PropertySource(value = "classpath:/bidder-config/facebook.yaml", factory = YamlPropertySourceFactory.class)
public class FacebookConfiguration {

    private static final String BIDDER_NAME = "audienceNetwork";

    @Autowired
    private JacksonMapper mapper;

    @Autowired
    @Qualifier("facebookConfigurationProperties")
    private FacebookConfigurationProperties configProperties;

    @Bean("facebookConfigurationProperties")
    @ConfigurationProperties("adapters.facebook")
    FacebookConfigurationProperties configurationProperties() {
        return new FacebookConfigurationProperties();
    }

    @Bean
    BidderDeps facebookBidderDeps(BidderConfigurationProperties facebookConfigurationProperties,
                                  @NotBlank @Value("${external-url}") String externalUrl,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.<FacebookConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .usersyncerCreator(UsersyncerCreator.create(null))
                .bidderCreator(configProperties.getEnabled()
                        ? config -> new FacebookBidder(
                        config.getEndpoint(),
                        config.getPlatformId(),
                        config.getAppSecret(),
                        configProperties.getTimeoutNotificationUrlTemplate(), mapper)
                        : null)
                .assemble();
    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class FacebookConfigurationProperties extends BidderConfigurationProperties {

        @NotNull
        private String platformId;

        @NotNull
        private String appSecret;

        @NotNull
        private String timeoutNotificationUrlTemplate;
    }
}
