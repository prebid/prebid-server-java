package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adkernel.AdkernelBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adkernel.yaml", factory = YamlPropertySourceFactory.class)
public class AdkernelConfiguration {

    private static final String BIDDER_NAME = "adkernel";

    @Bean("adkernelConfigurationProperties")
    @ConfigurationProperties("adapters.adkernel")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adkernelBidderDeps(BidderConfigurationProperties adkernelConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adkernelConfigurationProperties)
                .bidderCreator(config -> new AdkernelBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
