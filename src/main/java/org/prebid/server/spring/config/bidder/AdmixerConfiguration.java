package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.admixer.AdmixerBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/admixer.yaml", factory = YamlPropertySourceFactory.class)
public class AdmixerConfiguration {

    private static final String BIDDER_NAME = "admixer";

    @Bean("admixerConfigurationProperties")
    @ConfigurationProperties("adapters.admixer")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps admixerBidderDeps(BidderConfigurationProperties admixerConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(admixerConfigurationProperties)
                .bidderCreator(config -> new AdmixerBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
