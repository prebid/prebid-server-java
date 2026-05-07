package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.nobid.NobidBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/nobid.yaml", factory = YamlPropertySourceFactory.class)
public class NobidConfiguration {

    private static final String BIDDER_NAME = "nobid";

    @Bean("nobidConfigurationProperties")
    @ConfigurationProperties("adapters.nobid")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps nobidBidderDeps(BidderConfigurationProperties nobidConfigurationProperties,
                               JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(nobidConfigurationProperties)
                .bidderCreator(config -> new NobidBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
