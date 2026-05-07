package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.GenericBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/automatad.yaml", factory = YamlPropertySourceFactory.class)
public class AutomatadBidderConfiguration {

    private static final String BIDDER_NAME = "automatad";

    @Bean("automatadConfigurationProperties")
    @ConfigurationProperties("adapters.automatad")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps automatadBidderDeps(BidderConfigurationProperties automatadConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(automatadConfigurationProperties)
                .bidderCreator(config -> new GenericBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
