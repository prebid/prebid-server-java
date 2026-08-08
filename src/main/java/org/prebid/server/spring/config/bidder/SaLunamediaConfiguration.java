package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.salunamedia.SaLunamediaBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/salunamedia.yaml", factory = YamlPropertySourceFactory.class)
public class SaLunamediaConfiguration {

    private static final String BIDDER_NAME = "sa_lunamedia";

    @Bean("salunamediaConfigurationProperties")
    @ConfigurationProperties("adapters.salunamedia")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps salunamediaBidderDeps(BidderConfigurationProperties salunamediaConfigurationProperties,
                                     JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(salunamediaConfigurationProperties)
                .bidderCreator(config -> new SaLunamediaBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
