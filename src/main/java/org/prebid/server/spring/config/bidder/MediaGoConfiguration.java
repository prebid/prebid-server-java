package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.mediago.MediaGoBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/mediago.yaml", factory = YamlPropertySourceFactory.class)
public class MediaGoConfiguration {

    private static final String BIDDER_NAME = "mediago";

    @Bean("mediagoConfigurationProperties")
    @ConfigurationProperties("adapters.mediago")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps mediagoBidderDeps(BidderConfigurationProperties mediagoConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(mediagoConfigurationProperties)
                .bidderCreator(config -> new MediaGoBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
