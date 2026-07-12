package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.operaads.OperaadsBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/operaads.yaml", factory = YamlPropertySourceFactory.class)
public class OperaadsConfiguration {

    private static final String BIDDER_NAME = "operaads";

    @Bean("operaadsConfigurationProperties")
    @ConfigurationProperties("adapters.operaads")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps operaadsBidderDeps(BidderConfigurationProperties operaadsConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(operaadsConfigurationProperties)
                .bidderCreator(config -> new OperaadsBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
