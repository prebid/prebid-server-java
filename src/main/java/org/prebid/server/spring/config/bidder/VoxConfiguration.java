package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.vox.VoxBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/vox.yaml", factory = YamlPropertySourceFactory.class)
public class VoxConfiguration {

    private static final String BIDDER_NAME = "vox";

    @Bean("voxConfigurationProperties")
    @ConfigurationProperties("adapters.vox")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps voxBidderDeps(BidderConfigurationProperties voxConfigurationProperties,
                             JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(voxConfigurationProperties)
                .bidderCreator(config -> new VoxBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
