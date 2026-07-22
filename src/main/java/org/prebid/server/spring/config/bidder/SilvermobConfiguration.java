package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.silvermob.SilvermobBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/silvermob.yaml", factory = YamlPropertySourceFactory.class)
public class SilvermobConfiguration {

    private static final String BIDDER_NAME = "silvermob";

    @Bean("silvermobConfigurationProperties")
    @ConfigurationProperties("adapters.silvermob")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps silvermobBidderDeps(BidderConfigurationProperties silvermobConfigurationProperties,
                                   JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(silvermobConfigurationProperties)
                .bidderCreator(config -> new SilvermobBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
