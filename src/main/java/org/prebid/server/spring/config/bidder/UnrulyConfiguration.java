package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.unruly.UnrulyBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/unruly.yaml", factory = YamlPropertySourceFactory.class)
public class UnrulyConfiguration {

    private static final String BIDDER_NAME = "unruly";

    @Bean("unrulyConfigurationProperties")
    @ConfigurationProperties("adapters.unruly")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps unrulyBidderDeps(BidderConfigurationProperties unrulyConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(unrulyConfigurationProperties)
                .bidderCreator(config -> new UnrulyBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
