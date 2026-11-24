package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.viant.ViantBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/viant.yaml", factory = YamlPropertySourceFactory.class)
public class ViantConfiguration {

    private static final String BIDDER_NAME = "viant";

    @Bean("viantConfigurationProperties")
    @ConfigurationProperties("adapters.viant")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps viantBidderDeps(BidderConfigurationProperties viantConfigurationProperties,
                               JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(viantConfigurationProperties)
                .bidderCreator(config -> new ViantBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
