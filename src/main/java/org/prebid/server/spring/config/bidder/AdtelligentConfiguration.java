package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adtelligent.AdtelligentBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adtelligent.yaml", factory = YamlPropertySourceFactory.class)
public class AdtelligentConfiguration {

    private static final String BIDDER_NAME = "adtelligent";

    @Bean("adtelligentConfigurationProperties")
    @ConfigurationProperties("adapters.adtelligent")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adtelligentBidderDeps(BidderConfigurationProperties adtelligentConfigurationProperties,
                                     JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adtelligentConfigurationProperties)
                .bidderCreator(config -> new AdtelligentBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
