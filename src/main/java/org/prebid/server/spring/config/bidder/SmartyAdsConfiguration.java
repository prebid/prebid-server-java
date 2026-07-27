package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.smartyads.SmartyAdsBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/smartyads.yaml", factory = YamlPropertySourceFactory.class)
public class SmartyAdsConfiguration {

    private static final String BIDDER_NAME = "smartyads";

    @Bean("smartyadsConfigurationProperties")
    @ConfigurationProperties("adapters.smartyads")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps smartyadsBidderDeps(BidderConfigurationProperties smartyadsConfigurationProperties,
                                   JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(smartyadsConfigurationProperties)
                .bidderCreator(config -> new SmartyAdsBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
