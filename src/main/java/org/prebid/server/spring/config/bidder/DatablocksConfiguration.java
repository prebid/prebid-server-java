package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.datablocks.DatablocksBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/datablocks.yaml", factory = YamlPropertySourceFactory.class)
public class DatablocksConfiguration {

    private static final String BIDDER_NAME = "datablocks";

    @Bean("datablocksConfigurationProperties")
    @ConfigurationProperties("adapters.datablocks")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps datablocksBidderDeps(BidderConfigurationProperties datablocksConfigurationProperties,
                                    JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(datablocksConfigurationProperties)
                .bidderCreator(config -> new DatablocksBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
