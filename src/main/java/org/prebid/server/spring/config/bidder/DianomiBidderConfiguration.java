package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.dianomi.DianomiBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/dianomi.yaml", factory = YamlPropertySourceFactory.class)
public class DianomiBidderConfiguration {

    private static final String BIDDER_NAME = "dianomi";

    @Bean("dianomiConfigurationProperties")
    @ConfigurationProperties("adapters.dianomi")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps dianomiBidderDeps(BidderConfigurationProperties dianomiConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(dianomiConfigurationProperties)
                .bidderCreator(config -> new DianomiBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
