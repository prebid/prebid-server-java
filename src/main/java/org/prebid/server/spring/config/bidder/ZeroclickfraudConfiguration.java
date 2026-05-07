package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.zeroclickfraud.ZeroclickfraudBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/zeroclickfraud.yaml", factory = YamlPropertySourceFactory.class)
public class ZeroclickfraudConfiguration {

    private static final String BIDDER_NAME = "zeroclickfraud";

    @Bean("zeroclickfraudConfigurationProperties")
    @ConfigurationProperties("adapters.zeroclickfraud")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps zeroclickfraudBidderDeps(BidderConfigurationProperties zeroclickfraudConfigurationProperties,
                                        JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(zeroclickfraudConfigurationProperties)
                .bidderCreator(config -> new ZeroclickfraudBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
