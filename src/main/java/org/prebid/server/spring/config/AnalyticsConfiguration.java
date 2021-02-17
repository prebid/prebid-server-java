package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.AnalyticsReporterDelegator;
import org.prebid.server.analytics.LogAnalyticsReporter;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.json.JacksonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

@Configuration
public class AnalyticsConfiguration {

    @Bean
    AnalyticsReporterDelegator analyticsReporterDelegator(
            @Autowired(required = false) List<AnalyticsReporter> delegates,
            Vertx vertx,
            PrivacyEnforcementService privacyEnforcementService) {

        return new AnalyticsReporterDelegator(
                delegates != null ? delegates : Collections.emptyList(),
                vertx,
                privacyEnforcementService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "analytics.log", name = "enabled", havingValue = "true")
    LogAnalyticsReporter logAnalyticsReporter(JacksonMapper mapper) {
        return new LogAnalyticsReporter(mapper);
    }
}
