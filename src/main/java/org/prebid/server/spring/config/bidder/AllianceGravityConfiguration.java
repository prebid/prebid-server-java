package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.alliancegravity.AllianceGravityBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/alliancegravity.yaml", factory = YamlPropertySourceFactory.class)
public class AllianceGravityConfiguration {

    private static final String BIDDER_NAME = "alliance_gravity";

    @Bean("alliancegravityConfigurationProperties")
    @ConfigurationProperties("adapters.alliancegravity")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps alliancegravityBidderDeps(BidderConfigurationProperties alliancegravityConfigurationProperties,
                                         JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(alliancegravityConfigurationProperties)
                .bidderCreator(config -> new AllianceGravityBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
