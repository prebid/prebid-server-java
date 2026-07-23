package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.mediasquare.MediasquareBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/mediasquare.yaml", factory = YamlPropertySourceFactory.class)
public class MediasquareConfiguration {

    private static final String BIDDER_NAME = "mediasquare";

    @Bean("mediasquareConfigurationProperties")
    @ConfigurationProperties("adapters.mediasquare")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps mediasquareBidderDeps(BidderConfigurationProperties mediasquareConfigurationProperties,
                                     JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(mediasquareConfigurationProperties)
                .bidderCreator(config -> new MediasquareBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
