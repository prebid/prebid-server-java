package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.bizzclick.BizzclickBidder;
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
@PropertySource(value = "classpath:/bidder-config/bizzclick.yaml", factory = YamlPropertySourceFactory.class)
public class BizzclickConfiguration {

    private static final String BIDDER_NAME = "bizzclick";

    @Bean("bizzclickConfigurationProperties")
    @ConfigurationProperties("adapters.bizzclick")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps bizzclickBidderDeps(BidderConfigurationProperties bizzclickConfigurationProperties,
                                   @NotBlank @Value("${external-url}") String externalUrl,
                                   JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(bizzclickConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new BizzclickBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
