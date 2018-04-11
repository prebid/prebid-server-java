package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.CompositeAnalyticsReporter;
import org.springframework.beans.factory.annotation.Autowired;
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
}
