package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.dxkulture.DxKultureBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/dxkulture.yaml", factory = YamlPropertySourceFactory.class)
public class DxKultureBidderConfiguration {

    private static final String BIDDER_NAME = "dxkulture";

    @Bean("dxkultureConfigurationProperties")
    @ConfigurationProperties("adapters.dxkulture")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps dxkultureBidderDeps(BidderConfigurationProperties dxkultureConfigurationProperties,
                              JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(dxkultureConfigurationProperties)
                .bidderCreator(config -> new DxKultureBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
