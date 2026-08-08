package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.edge226.Edge226Bidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/edge226.yaml", factory = YamlPropertySourceFactory.class)
public class Edge226Configuration {

    private static final String BIDDER_NAME = "edge226";

    @Bean("edge226ConfigurationProperties")
    @ConfigurationProperties("adapters.edge226")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps edge226BidderDeps(BidderConfigurationProperties edge226ConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(edge226ConfigurationProperties)
                .bidderCreator(config -> new Edge226Bidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
