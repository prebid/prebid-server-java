package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.advangelists.AdvangelistsBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/advangelists.yaml", factory = YamlPropertySourceFactory.class)
public class AdvangelistsConfiguration {

    private static final String BIDDER_NAME = "advangelists";

    @Bean("advangelistsConfigurationProperties")
    @ConfigurationProperties("adapters.advangelists")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps advangelistsBidderDeps(BidderConfigurationProperties advangelistsConfigurationProperties,
                                      JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(advangelistsConfigurationProperties)
                .bidderCreator(config -> new AdvangelistsBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
