package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.jixie.JixieBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/jixie.yaml", factory = YamlPropertySourceFactory.class)
public class JixieConfiguration {

    private static final String BIDDER_NAME = "jixie";

    @Bean("jixieConfigurationProperties")
    @ConfigurationProperties("adapters.jixie")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps jixieBidderDeps(BidderConfigurationProperties jixieConfigurationProperties,
                               JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(jixieConfigurationProperties)
                .bidderCreator(config -> new JixieBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
