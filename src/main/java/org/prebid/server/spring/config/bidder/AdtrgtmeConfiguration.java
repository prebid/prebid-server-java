package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.adtrgtme.AdtrgtmeBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/adtrgtme.yaml", factory = YamlPropertySourceFactory.class)
public class AdtrgtmeConfiguration {

    private static final String BIDDER_NAME = "adtrgtme";

    @Bean("adtrgtmeConfigurationProperties")
    @ConfigurationProperties("adapters.adtrgtme")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps adtrgtmeBidderDeps(BidderConfigurationProperties adtrgtmeConfigurationProperties,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(adtrgtmeConfigurationProperties)
                .bidderCreator(config -> new AdtrgtmeBidder(config.getEndpoint(), mapper))
                .assemble();
    }

}
