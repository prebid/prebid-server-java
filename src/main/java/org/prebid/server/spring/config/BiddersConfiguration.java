package org.prebid.server.spring.config;

import org.prebid.server.spring.config.bidder.model.DefaultBidderConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BiddersConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "adapter-defaults")
    DefaultBidderConfigurationProperties defaultBidderConfigurationProperties() {
        return new DefaultBidderConfigurationProperties();
    }
}
