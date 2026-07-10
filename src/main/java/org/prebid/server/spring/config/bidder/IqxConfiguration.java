package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.iqx.IqxBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/iqx.yaml", factory = YamlPropertySourceFactory.class)
public class IqxConfiguration {

    private static final String BIDDER_NAME = "iqx";

    @Bean("iqxConfigurationProperties")
    @ConfigurationProperties("adapters.iqx")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps iqxBidderDeps(BidderConfigurationProperties iqxConfigurationProperties,
                             JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(iqxConfigurationProperties)
                .bidderCreator(config -> new IqxBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
