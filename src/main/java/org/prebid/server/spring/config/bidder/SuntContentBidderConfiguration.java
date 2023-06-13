package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.seedingAlliance.SeedingAllianceBidder;
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

@Configuration
@PropertySource(value = "classpath:/bidder-config/suntContent.yaml", factory = YamlPropertySourceFactory.class)
public class SuntContentBidderConfiguration {

    private static final String BIDDER_NAME = "suntContent";

    @Bean("suntContentConfigurationProperties")
    @ConfigurationProperties("adapters.suntcontent")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps suntContentBidderDeps(BidderConfigurationProperties suntContentConfigurationProperties,
                                         @NotBlank @Value("${external-url}") String externalUrl,
                                         JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(suntContentConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                // Temporary use of SeedingAllianceBidder before divergent changes will appear
                .bidderCreator(config -> new SeedingAllianceBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
