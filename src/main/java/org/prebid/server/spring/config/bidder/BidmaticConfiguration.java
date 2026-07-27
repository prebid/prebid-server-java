package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.bidmatic.BidmaticBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/bidmatic.yaml", factory = YamlPropertySourceFactory.class)
public class BidmaticConfiguration {

    private static final String BIDDER_NAME = "bidmatic";

    @Bean("bidmaticConfigurationProperties")
    @ConfigurationProperties("adapters.bidmatic")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps bidmaticBidderDeps(BidderConfigurationProperties bidmaticConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(bidmaticConfigurationProperties)
                .bidderCreator(config -> new BidmaticBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
