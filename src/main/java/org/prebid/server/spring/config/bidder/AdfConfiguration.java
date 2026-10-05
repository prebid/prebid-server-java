package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adf.AdfBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adf.yaml", factory = YamlPropertySourceFactory.class)
public class AdfConfiguration {

    private static final String BIDDER_NAME = "adf";

    @Bean("adfConfigurationProperties")
    @ConfigurationProperties("adapters.adf")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adfBidderDeps(BidderConfigurationProperties adfConfigurationProperties,
                             JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adfConfigurationProperties)
                .bidderCreator(config -> new AdfBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
