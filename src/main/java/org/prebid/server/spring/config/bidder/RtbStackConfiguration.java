package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.rtbstack.RtbStackBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/rtbstack.yaml", factory = YamlPropertySourceFactory.class)
public class RtbStackConfiguration {

    private static final String BIDDER_NAME = "rtbstack";

    @Bean("rtbstackConfigurationProperties")
    @ConfigurationProperties("adapters.rtbstack")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps rtbstackBidderDeps(BidderConfigurationProperties rtbstackConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(rtbstackConfigurationProperties)
                .bidderCreator(config -> new RtbStackBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
