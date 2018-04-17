package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.CompositeAnalyticsReporter;
import org.prebid.server.analytics.LogAnalyticsReporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

@Configuration
public class AnalyticsConfiguration {

    @Bean
    CompositeAnalyticsReporter compositeAnalyticsReporter(
            @Autowired(required = false) List<AnalyticsReporter> delegates, Vertx vertx) {

        return new CompositeAnalyticsReporter(delegates != null ? delegates : Collections.emptyList(), vertx);
    }

    @Bean
    @ConditionalOnProperty(prefix = "analytics.log", name = "enabled", havingValue = "true")
    LogAnalyticsReporter logAnalyticsReporter() {
        return new LogAnalyticsReporter();
    }
}
