package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.loopme.LoopmeBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/loopme.yaml", factory = YamlPropertySourceFactory.class)
public class LoopmeConfiguration {

    private static final String BIDDER_NAME = "loopme";

    @Bean("loopmeConfigurationProperties")
    @ConfigurationProperties("adapters.loopme")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps loopmeBidderDeps(BidderConfigurationProperties loopmeConfigurationProperties,
                                JacksonMapper mapper) {
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(loopmeConfigurationProperties)
                .bidderCreator(config -> new LoopmeBidder(loopmeConfigurationProperties.getEndpoint(), mapper))
                .assemble();
    }
}
