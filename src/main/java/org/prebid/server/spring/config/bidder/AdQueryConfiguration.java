package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adquery.AdQueryBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adquery.yaml", factory = YamlPropertySourceFactory.class)
public class AdQueryConfiguration {

    private static final String BIDDER_NAME = "adquery";

    @Bean("adqueryConfigurationProperties")
    @ConfigurationProperties("adapters.adquery")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adqueryBidderDeps(BidderConfigurationProperties adqueryConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adqueryConfigurationProperties)
                .bidderCreator(config -> new AdQueryBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
