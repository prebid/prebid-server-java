package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.nativery.NativeryBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/nativery.yaml", factory = YamlPropertySourceFactory.class)
public class NativeryBidderConfiguration {

    private static final String BIDDER_NAME = "nativery";

    @Bean("nativeryConfigurationProperties")
    @ConfigurationProperties("adapters.nativery")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps nativeryBidderDeps(BidderConfigurationProperties nativeryConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(nativeryConfigurationProperties)
                .bidderCreator(config -> new NativeryBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
