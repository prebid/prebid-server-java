package org.prebid.server.spring.config;

import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.CompositeAnalyticsReporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

@Configuration
public class AnalyticsConfiguration {

    @Autowired(required = false)
    private List<AnalyticsReporter> delegates = Collections.emptyList();

    @Bean
    CompositeAnalyticsReporter compositeAnalyticsReporter() {
        return new CompositeAnalyticsReporter(delegates);
    }
}
