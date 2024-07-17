package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.cointraffic.CointrafficBidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.UsersyncerCreator;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import jakarta.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/cointraffic.yaml", factory = YamlPropertySourceFactory.class)
public class CointrafficConfiguration {

    private static final String BIDDER_NAME = "cointraffic";

    @Bean("cointrafficConfigurationProperties")
    @ConfigurationProperties("adapters.cointraffic")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps cointrafficBidderDeps(BidderConfigurationProperties cointrafficConfigurationProperties,
                                     @NotBlank @Value("${external-url}") String externalUrl,
                                     JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(cointrafficConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new CointrafficBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
