package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.smartx.SmartxBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/smartx.yaml", factory = YamlPropertySourceFactory.class)
public class SmartxConfiguration {

    private static final String BIDDER_NAME = "smartx";

    @Bean("smartxConfigurationProperties")
    @ConfigurationProperties("adapters.smartx")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps smartxBidderDeps(BidderConfigurationProperties smartxConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(smartxConfigurationProperties)
                .bidderCreator(config -> new SmartxBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
