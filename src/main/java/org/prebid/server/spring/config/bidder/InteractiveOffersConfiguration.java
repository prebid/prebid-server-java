package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.interactiveoffers.InteractiveOffersBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/interactiveoffers.yaml", factory = YamlPropertySourceFactory.class)
public class InteractiveOffersConfiguration {

    private static final String BIDDER_NAME = "interactiveoffers";

    @Bean("interactiveoffersConfigurationProperties")
    @ConfigurationProperties("adapters.interactiveoffers")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps interactiveoffersBidderDeps(BidderConfigurationProperties interactiveoffersConfigurationProperties,
                                           JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(interactiveoffersConfigurationProperties)
                .bidderCreator(config -> new InteractiveOffersBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
