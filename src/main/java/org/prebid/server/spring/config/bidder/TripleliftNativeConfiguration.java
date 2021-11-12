package org.prebid.server.spring.config.bidder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.tripleliftnative.TripleliftNativeBidder;
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
import java.util.List;

@Configuration
@PropertySource(value = "classpath:/bidder-config/tripleliftnative.yaml", factory = YamlPropertySourceFactory.class)
public class TripleliftNativeConfiguration {

    private static final String BIDDER_NAME = "triplelift_native";

    @Autowired
    @Qualifier("tripleliftNativeConfigurationProperties")
    private TripleliftNativeConfigurationProperties configurationProperties;

    @Bean("tripleliftNativeConfigurationProperties")
    @ConfigurationProperties("adapters.tripleliftnative")
    TripleliftNativeConfigurationProperties configurationProperties() {
        return new TripleliftNativeConfigurationProperties();
    }

    @Bean
    BidderDeps tripleliftNativeBidderDeps(TripleliftNativeConfigurationProperties tripleliftNativeConfigurationProperties,
                                          @NotBlank @Value("${external-url}") String externalUrl,
                                          JacksonMapper mapper) {
        final List<String> whitelist = configurationProperties.getWhitelist();
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config ->
                        new TripleliftNativeBidder(
                                config.getEndpoint(),
                                configurationProperties.getWhitelist(),
                                mapper))
                .assemble();
    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TripleliftNativeConfigurationProperties extends BidderConfigurationProperties {

        private List<String> whitelist;
    }
}
