package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.yieldlab.YieldlabBidder;
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
import java.time.Clock;

@Configuration
@PropertySource(value = "classpath:/bidder-config/yieldlab.yaml", factory = YamlPropertySourceFactory.class)
public class YieldlabConfiguration {

    private static final String BIDDER_NAME = "yieldlab";

    @Bean("yieldlabConfigurationProperties")
    @ConfigurationProperties("adapters.yieldlab")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps yieldlabBidderDeps(BidderConfigurationProperties yieldlabConfigurationProperties,
                                  @NotBlank @Value("${external-url}") String externalUrl,
                                  Clock clock,
                                  JacksonMapper mapper) {

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(yieldlabConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new YieldlabBidder(config.getEndpoint(), clock, mapper))
                .assemble();
    }
}
