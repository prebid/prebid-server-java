package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.ownadx.OwnAdxBidder;
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

import javax.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/ownadx.yaml", factory = YamlPropertySourceFactory.class)
public class OwnAdxBidderConfiguration {

    private static final String BIDDER_NAME = "ownadx";

    @Bean("ownAdxConfigurationProperties")
    @ConfigurationProperties("adapters.ownadx")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps ownAdxBidderDeps(BidderConfigurationProperties ownAdxConfigurationProperties,
                                 @NotBlank @Value("${external-url}") String externalUrl,
                                 JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(ownAdxConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new OwnAdxBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
