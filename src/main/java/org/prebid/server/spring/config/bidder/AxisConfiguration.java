package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.axis.AxisBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/axis.yaml", factory = YamlPropertySourceFactory.class)
public class AxisConfiguration {

    private static final String BIDDER_NAME = "axis";

    @Bean("axisConfigurationProperties")
    @ConfigurationProperties("adapters.axis")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps axisBidderDeps(BidderConfigurationProperties axisConfigurationProperties,
                              JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(axisConfigurationProperties)
                .bidderCreator(config -> new AxisBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
