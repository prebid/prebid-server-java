package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.teads.TeadsBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/teads.yaml", factory = YamlPropertySourceFactory.class)
public class TeadsConfiguration {

    private static final String BIDDER_NAME = "teads";

    @Bean("teadsConfigurationProperties")
    @ConfigurationProperties("adapters.teads")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps teadsBidderDeps(BidderConfigurationProperties teadsConfigurationProperties,
                               JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(teadsConfigurationProperties)
                .bidderCreator(config -> new TeadsBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
