package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.invibes.InvibesBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/invibes.yaml", factory = YamlPropertySourceFactory.class)
public class InvibesConfiguration {

    private static final String BIDDER_NAME = "invibes";

    @Bean("invibesConfigurationProperties")
    @ConfigurationProperties("adapters.invibes")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps invibesBidderDeps(BidderConfigurationProperties invibesConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(invibesConfigurationProperties)
                .bidderCreator(config -> new InvibesBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
