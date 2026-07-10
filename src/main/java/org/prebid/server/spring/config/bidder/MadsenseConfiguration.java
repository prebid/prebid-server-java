package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.madsense.MadsenseBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/madsense.yaml", factory = YamlPropertySourceFactory.class)
public class MadsenseConfiguration {

    private static final String BIDDER_NAME = "madsense";

    @Bean("madsenseConfigurationProperties")
    @ConfigurationProperties("adapters.madsense")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps madsenseBidderDeps(BidderConfigurationProperties madsenseConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(madsenseConfigurationProperties)
                .bidderCreator(config -> new MadsenseBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
