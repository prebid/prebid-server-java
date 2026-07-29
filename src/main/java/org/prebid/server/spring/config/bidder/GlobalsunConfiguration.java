package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.globalsun.GlobalsunBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/globalsun.yaml", factory = YamlPropertySourceFactory.class)
public class GlobalsunConfiguration {

    private static final String BIDDER_NAME = "globalsun";

    @Bean("globalsunConfigurationProperties")
    @ConfigurationProperties("adapters.globalsun")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps globalsunBidderDeps(BidderConfigurationProperties globalsunConfigurationProperties,
                                   JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(globalsunConfigurationProperties)
                .bidderCreator(config -> new GlobalsunBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
