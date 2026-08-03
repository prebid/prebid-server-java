package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.synapsehx.SynapseHXBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/synapsehx.yaml", factory = YamlPropertySourceFactory.class)
public class SynapseHXBidderConfiguration {

    private static final String BIDDER_NAME = "synapsehx";

    @Bean("synapsehxConfigurationProperties")
    @ConfigurationProperties("adapters.synapsehx")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps synapsehxBidderDeps(BidderConfigurationProperties synapsehxConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(synapsehxConfigurationProperties)
                .bidderCreator(config -> new SynapseHXBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
