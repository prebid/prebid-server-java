package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.epom.EpomBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/epom.yaml", factory = YamlPropertySourceFactory.class)
public class EpomConfiguration {

    private static final String BIDDER_NAME = "epom";

    @Bean("epomConfigurationProperties")
    @ConfigurationProperties("adapters.epom")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps epomBidderDeps(BidderConfigurationProperties epomConfigurationProperties,
                              JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(epomConfigurationProperties)
                .bidderCreator(config -> new EpomBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
