package org.prebid.server.hooks.modules.ortb2.blocking.spring.config;

import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.hooks.modules.ortb2.blocking.v1.Ortb2BlockingModule;
import org.prebid.server.json.ObjectMapperProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(prefix = "hooks." + Ortb2BlockingModule.CODE, name = "enabled", havingValue = "true")
@Configuration
public class Ortb2BlockingModuleConfiguration {

    @Bean
    Ortb2BlockingModule ortb2BlockingModule(BidderCatalog bidderCatalog) {
        return new Ortb2BlockingModule(bidderCatalog, ObjectMapperProvider.mapper());
    }
}
