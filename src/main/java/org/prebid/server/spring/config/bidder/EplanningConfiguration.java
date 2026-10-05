package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.eplanning.EplanningBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/eplanning.yaml", factory = YamlPropertySourceFactory.class)
public class EplanningConfiguration {

    private static final String BIDDER_NAME = "eplanning";

    @Bean("eplanningConfigurationProperties")
    @ConfigurationProperties("adapters.eplanning")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps eplanningBidderDeps(BidderConfigurationProperties eplanningConfigurationProperties,
                                   JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(eplanningConfigurationProperties)
                .bidderCreator(config -> new EplanningBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
