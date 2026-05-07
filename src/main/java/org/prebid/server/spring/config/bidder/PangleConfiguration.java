package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.pangle.PangleBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/pangle.yaml", factory = YamlPropertySourceFactory.class)
public class PangleConfiguration {

    private static final String BIDDER_NAME = "pangle";

    @Bean("pangleConfigurationProperties")
    @ConfigurationProperties("adapters.pangle")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps pangleBidderDeps(BidderConfigurationProperties pangleConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(pangleConfigurationProperties)
                .bidderCreator(config -> new PangleBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
