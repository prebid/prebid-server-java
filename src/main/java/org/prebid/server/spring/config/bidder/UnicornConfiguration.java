package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.unicorn.UnicornBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/unicorn.yaml", factory = YamlPropertySourceFactory.class)
public class UnicornConfiguration {

    private static final String BIDDER_NAME = "unicorn";

    @Bean("unicornConfigurationProperties")
    @ConfigurationProperties("adapters.unicorn")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps unicornBidderDeps(BidderConfigurationProperties unicornConfigurationProperties,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(unicornConfigurationProperties)
                .bidderCreator(config -> new UnicornBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
