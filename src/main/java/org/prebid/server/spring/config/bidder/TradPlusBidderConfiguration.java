package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.tradplus.TradPlusBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/tradplus.yaml", factory = YamlPropertySourceFactory.class)
public class TradPlusBidderConfiguration {

    private static final String BIDDER_NAME = "tradplus";

    @Bean("tradplusConfigurationProperties")
    @ConfigurationProperties("adapters.tradplus")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps tradplusBidderDeps(BidderConfigurationProperties tradplusConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(tradplusConfigurationProperties)
                .bidderCreator(config -> new TradPlusBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
