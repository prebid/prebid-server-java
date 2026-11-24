package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.hypelab.HypeLabBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.prebid.server.version.PrebidVersionProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/hypelab.yaml", factory = YamlPropertySourceFactory.class)
public class HypeLabConfiguration {

    private static final String BIDDER_NAME = "hypelab";

    @Bean("hypelabConfigurationProperties")
    @ConfigurationProperties("adapters.hypelab")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps hypelabBidderDeps(BidderConfigurationProperties hypelabConfigurationProperties,
                                 PrebidVersionProvider prebidVersionProvider,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(hypelabConfigurationProperties)
                .bidderCreator(config -> new HypeLabBidder(
                        config.getEndpoint(),
                        mapper,
                        prebidVersionProvider))
                .assemble();
    }
}
