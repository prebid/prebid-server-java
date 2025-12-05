package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.msft.MsftBidder;
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
import java.util.Map;

@Configuration
@PropertySource(value = "classpath:/bidder-config/msft.yaml", factory = YamlPropertySourceFactory.class)
public class MsftConfiguration {

    private static final String BIDDER_NAME = "msft";

    @Bean("msftConfigurationProperties")
    @ConfigurationProperties("adapters.msft")
    MsftConfigurationProperties configurationProperties() {
        return new MsftConfigurationProperties();
    }

    @Bean
    BidderDeps msftBidderDeps(MsftConfigurationProperties msftConfigurationProperties,
                              @NotBlank @Value("${external-url}") String externalUrl,
                              JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(msftConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new MsftBidder(
                        config.getEndpoint(),
                        msftConfigurationProperties.getHbSource(),
                        msftConfigurationProperties.getHbSourceVideo(),
                        msftConfigurationProperties.getIabCategories(),
                        mapper))
                .assemble();
    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class MsftConfigurationProperties extends BidderConfigurationProperties {

        @NotNull
        Integer hbSource = 5;

        @NotNull
        Integer hbSourceVideo = 6;

        Map<Integer, String> iabCategories;
    }
}
