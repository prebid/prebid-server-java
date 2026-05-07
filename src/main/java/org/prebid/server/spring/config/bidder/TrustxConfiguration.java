package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.trustx.TrustxBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/trustx.yaml", factory = YamlPropertySourceFactory.class)
public class TrustxConfiguration {

    private static final String BIDDER_NAME = "trustx";

    @Bean("trustxConfigurationProperties")
    @ConfigurationProperties("adapters.trustx")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps trustxBidderDeps(BidderConfigurationProperties trustxConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(trustxConfigurationProperties)
                .bidderCreator(config -> new TrustxBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
