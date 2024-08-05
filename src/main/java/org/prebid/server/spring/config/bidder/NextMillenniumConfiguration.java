package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.nextmillennium.NextMillenniumBidder;
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
import java.util.List;

@Configuration
@PropertySource(value = "classpath:/bidder-config/nextmillennium.yaml", factory = YamlPropertySourceFactory.class)
public class NextMillenniumConfiguration {

    private static final String BIDDER_NAME = "nextmillennium";

    @Bean("nextMillenniumConfigurationProperties")
    @ConfigurationProperties("adapters.nextmillennium")
    NextMillenniumConfigurationProperties configurationProperties() {
        return new NextMillenniumConfigurationProperties();
    }

    @Bean
    BidderDeps nextMillenniumBidderDeps(NextMillenniumConfigurationProperties nextMillenniumConfigurationProperties,
                                        @NotBlank @Value("${external-url}") String externalUrl,
                                        JacksonMapper mapper) {

        return BidderDepsAssembler.<NextMillenniumConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(nextMillenniumConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new NextMillenniumBidder(
                        config.getEndpoint(),
                        mapper,
                        config.getExtraInfo().getNmmFlags())
                ).assemble();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class NextMillenniumConfigurationProperties extends BidderConfigurationProperties {

        private ExtraInfo extraInfo = new ExtraInfo();
    }

    @Data
    @NoArgsConstructor
    private static class ExtraInfo {

        List<String> nmmFlags;
    }
}
