package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.kueezrtb.KueezRtbBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/kueezrtb.yaml", factory = YamlPropertySourceFactory.class)
public class KueezRtbConfiguration {

    private static final String BIDDER_NAME = "kueezrtb";

    @Bean("kueezrtbConfigurationProperties")
    @ConfigurationProperties("adapters.kueezrtb")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps kueezrtbBidderDeps(BidderConfigurationProperties kueezrtbConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(kueezrtbConfigurationProperties)
                .bidderCreator(config -> new KueezRtbBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
