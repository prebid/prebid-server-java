package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.goadserver.GoadserverBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/goadserver.yaml", factory = YamlPropertySourceFactory.class)
public class GoadserverConfiguration {

    private static final String BIDDER_NAME = "goadserver";

    @Bean("goadserverConfigurationProperties")
    @ConfigurationProperties("adapters.goadserver")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps goadserverBidderDeps(BidderConfigurationProperties goadserverConfigurationProperties,
                                    JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(goadserverConfigurationProperties)
                .bidderCreator(config -> new GoadserverBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
