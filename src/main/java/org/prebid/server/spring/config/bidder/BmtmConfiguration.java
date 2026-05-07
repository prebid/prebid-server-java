package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.bmtm.BmtmBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/bmtm.yaml", factory = YamlPropertySourceFactory.class)
public class BmtmConfiguration {

    private static final String BIDDER_NAME = "bmtm";

    @Bean("bmtmConfigurationProperties")
    @ConfigurationProperties("adapters.bmtm")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps bmtmBidderDeps(BidderConfigurationProperties bmtmConfigurationProperties,
                              JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(bmtmConfigurationProperties)
                .bidderCreator(config -> new BmtmBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
