package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.appnexus.AppnexusBidder;
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

import javax.validation.constraints.NotBlank;
import java.util.Map;

@Configuration
@PropertySource(value = "classpath:/bidder-config/appnexus.yaml", factory = YamlPropertySourceFactory.class)
public class AppnexusConfiguration {

    private static final String BIDDER_NAME = "appnexus";

    @Bean("appnexusConfigurationProperties")
    @ConfigurationProperties("adapters.appnexus")
    AppnexusConfigurationProperties configurationProperties() {
        return new AppnexusConfigurationProperties();
    }

    @Bean
    BidderDeps appnexusBidderDeps(AppnexusConfigurationProperties appnexusConfigurationProperties,
                                  @NotBlank @Value("${external-url}") String externalUrl,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(appnexusConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new AppnexusBidder(config.getEndpoint(),
                        appnexusConfigurationProperties.getPlatformId(),
                        appnexusConfigurationProperties.getIabCategories(),
                        mapper))
                .assemble();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class AppnexusConfigurationProperties extends BidderConfigurationProperties {

        Integer platformId;

        Map<Integer, String> iabCategories;
    }
}
