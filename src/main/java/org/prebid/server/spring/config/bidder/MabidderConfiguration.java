package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.mabidder.MabidderBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/mabidder.yaml", factory = YamlPropertySourceFactory.class)
public class MabidderConfiguration {

    private static final String BIDDER_NAME = "mabidder";

    @Bean("mabidderConfigurationProperties")
    @ConfigurationProperties("adapters.mabidder")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps mabidderDeps(BidderConfigurationProperties mabidderConfigurationProperties,
                            JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(mabidderConfigurationProperties)
                .bidderCreator(config -> new MabidderBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
