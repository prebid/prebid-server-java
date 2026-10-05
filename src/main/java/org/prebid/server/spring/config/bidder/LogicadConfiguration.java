package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.logicad.LogicadBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/logicad.yaml", factory = YamlPropertySourceFactory.class)
public class LogicadConfiguration {

    private static final String BIDDER_NAME = "logicad";

    @Bean("logicadConfigurationProperties")
    @ConfigurationProperties("adapters.logicad")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps logicadBidderDeps(BidderConfigurationProperties logicadConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(logicadConfigurationProperties)
                .bidderCreator(config -> new LogicadBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
