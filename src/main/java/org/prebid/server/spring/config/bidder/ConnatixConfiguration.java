package org.prebid.server.spring.config.bidder;

import jakarta.validation.constraints.NotBlank;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.connatix.ConnatixBidder;
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

@Configuration
@PropertySource(value = "classpath:/bidder-config/connatix.yaml", factory = YamlPropertySourceFactory.class)
public class ConnatixConfiguration {

    private static final String BIDDER_NAME = "connatix";

    @Bean("connatixConfigurationProperties")
    @ConfigurationProperties("adapters.connatix")
    BidderConfigurationProperties configurationProperties() { return new BidderConfigurationProperties(); }

    @Bean
    BidderDeps connatixBidderDeps(BidderConfigurationProperties connatixConfigurationProperties,
                                  @NotBlank @Value("http://localhost:8080") String externalUrl, JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(connatixConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new ConnatixBidder(config.getEndpoint(), mapper))
                .assemble();
    }
}
