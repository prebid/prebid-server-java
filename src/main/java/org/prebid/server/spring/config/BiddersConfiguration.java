package org.prebid.server.spring.config;

import org.prebid.server.spring.config.bidder.model.CommonBidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.postprocessor.BidderConfigurationBeanPostProcessor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BiddersConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "adapter-defaults")
    CommonBidderConfigurationProperties commonBidderConfigurationProperties() {
        return new CommonBidderConfigurationProperties();
    }

    @Bean
    BidderConfigurationBeanPostProcessor bidderConfigurationBeanPostProcessor(
            CommonBidderConfigurationProperties commonBidderConfigurationProperties) {
        return new BidderConfigurationBeanPostProcessor(commonBidderConfigurationProperties);
    }
}
