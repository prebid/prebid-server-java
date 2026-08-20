package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adplayx.AdplayxBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adplayx.yaml", factory = YamlPropertySourceFactory.class)
public class AdplayxConfiguration {

    private static final String BIDDER_NAME = "adplayx";

    @Bean("adplayxConfigurationProperties")
    @ConfigurationProperties("adapters.adplayx")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adplayxBidderDeps(BidderConfigurationProperties adplayxConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adplayxConfigurationProperties)
                .bidderCreator(config -> new AdplayxBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
