package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.matterfull.MatterfullrtbBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/matterfullrtb.yaml", factory = YamlPropertySourceFactory.class)
public class MatterfullrtbConfiguration {

    private static final String BIDDER_NAME = "matterfullrtb";

    @Bean("matterfullrtbConfigurationProperties")
    @ConfigurationProperties("adapters.matterfullrtb")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps matterfullrtbBidderDeps(BidderConfigurationProperties matterfullrtbConfigurationProperties,
                                       JacksonMapper mapper) {
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(matterfullrtbConfigurationProperties)
                .bidderCreator(config -> new MatterfullrtbBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
