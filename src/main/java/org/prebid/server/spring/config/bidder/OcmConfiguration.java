package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.ocm.OcmBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/ocm.yaml", factory = YamlPropertySourceFactory.class)
public class OcmConfiguration {

    private static final String BIDDER_NAME = "ocm";

    @Bean("ocmConfigurationProperties")
    @ConfigurationProperties("adapters.ocm")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps ocmBidderDeps(BidderConfigurationProperties ocmConfigurationProperties,
                             JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(ocmConfigurationProperties)
                .bidderCreator(config -> new OcmBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
