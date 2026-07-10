package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.iqzone.IqzoneBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/iqzone.yaml", factory = YamlPropertySourceFactory.class)
public class IqzoneConfiguration {

    private static final String BIDDER_NAME = "iqzone";

    @Bean("iqzoneConfigurationProperties")
    @ConfigurationProperties("adapters.iqzone")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps iqzoneBidderDeps(BidderConfigurationProperties iqzoneConfigurationProperties,
                                JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(iqzoneConfigurationProperties)
                .bidderCreator(config -> new IqzoneBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
