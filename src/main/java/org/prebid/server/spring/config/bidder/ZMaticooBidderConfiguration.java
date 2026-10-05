package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.zmaticoo.ZMaticooBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/zmaticoo.yaml", factory = YamlPropertySourceFactory.class)
public class ZMaticooBidderConfiguration {

    private static final String BIDDER_NAME = "zmaticoo";

    @Bean("zmaticooConfigurationProperties")
    @ConfigurationProperties("adapters.zmaticoo")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps zmaticooBidderDeps(BidderConfigurationProperties zmaticooConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(zmaticooConfigurationProperties)
                .bidderCreator(config -> new ZMaticooBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
