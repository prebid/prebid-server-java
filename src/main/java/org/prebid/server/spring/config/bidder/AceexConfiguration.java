package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.aceex.AceexBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/aceex.yaml", factory = YamlPropertySourceFactory.class)
public class AceexConfiguration {

    private static final String BIDDER_NAME = "aceex";

    @Bean("aceexConfigurationProperties")
    @ConfigurationProperties("adapters.aceex")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps aceexBidderDeps(BidderConfigurationProperties aceexConfigurationProperties,
                               JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(aceexConfigurationProperties)
                .bidderCreator(config -> new AceexBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
