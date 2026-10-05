package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.deepintent.DeepintentBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/deepintent.yaml", factory = YamlPropertySourceFactory.class)
public class DeepintentConfiguration {

    private static final String BIDDER_NAME = "deepintent";

    @Bean("deepintentConfigurationProperties")
    @ConfigurationProperties("adapters.deepintent")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps deepintentBidderDeps(BidderConfigurationProperties deepintentConfigurationProperties,
                                    JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(deepintentConfigurationProperties)
                .bidderCreator(config -> new DeepintentBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
