package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.sovrnxsp.SovrnXspBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/sovrnXsp.yaml", factory = YamlPropertySourceFactory.class)
public class SovrnXspConfiguration {

    private static final String BIDDER_NAME = "sovrnXsp";

    @Bean("sovrnXspConfigurationProperties")
    @ConfigurationProperties("adapters.sovrnxsp")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps sovrnXspBidderDeps(BidderConfigurationProperties sovrnXspConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(sovrnXspConfigurationProperties)
                .bidderCreator(config -> new SovrnXspBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
