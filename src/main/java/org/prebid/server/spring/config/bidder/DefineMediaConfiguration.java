package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.definemedia.DefineMediaBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/definemedia.yaml", factory = YamlPropertySourceFactory.class)
public class DefineMediaConfiguration {

    private static final String BIDDER_NAME = "definemedia";

    @Bean("definemediaConfigurationProperties")
    @ConfigurationProperties("adapters.definemedia")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps definemediaBidderDeps(BidderConfigurationProperties definemediaConfigurationProperties,
                             JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(definemediaConfigurationProperties)
                .bidderCreator(config -> new DefineMediaBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
