package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.connectad.ConnectAdBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/connectad.yaml", factory = YamlPropertySourceFactory.class)
public class ConnectAdConfiguration {

    private static final String BIDDER_NAME = "connectad";

    @Bean("connectadConfigurationProperties")
    @ConfigurationProperties("adapters.connectad")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps connectadBidderDeps(BidderConfigurationProperties connectadConfigurationProperties,
                                   JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(connectadConfigurationProperties)
                .bidderCreator(config -> new ConnectAdBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
