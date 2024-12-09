package org.prebid.server.it.hooks;

import org.mockito.Mockito;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.json.JacksonMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestHooksConfiguration {

    @Bean
    Module sampleItModule(JacksonMapper mapper) {
        return new SampleItModule(mapper);
    }

    @Bean
    @Primary
    AnalyticsReporterDelegator spyAnalyticsReporterDelegator(AnalyticsReporterDelegator analyticsReporterDelegator) {
        return Mockito.spy(analyticsReporterDelegator);
    }
}
