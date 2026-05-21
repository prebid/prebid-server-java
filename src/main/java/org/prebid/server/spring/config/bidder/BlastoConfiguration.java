package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.blasto.BlastoBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/blasto.yaml", factory = YamlPropertySourceFactory.class)
public class BlastoConfiguration {

    private static final String BIDDER_NAME = "blasto";

    @Bean("blastoConfigurationProperties")
    @ConfigurationProperties("adapters.blasto")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps blastoBidderDeps(BidderConfigurationProperties blastoConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(blastoConfigurationProperties)
                .bidderCreator(config -> new BlastoBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
