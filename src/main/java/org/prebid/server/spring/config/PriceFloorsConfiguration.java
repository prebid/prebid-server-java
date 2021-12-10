package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.floors.PriceFloorFetcher;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PriceFloorsConfiguration {

    @Bean
    PriceFloorFetcher priceFloorFetcher(
            ApplicationSettings applicationSettings,
            Metrics metrics,
            Vertx vertx,
            TimeoutFactory timeoutFactory,
            HttpClient httpClient,
            JacksonMapper mapper) {

        return new PriceFloorFetcher(
                applicationSettings,
                metrics,
                vertx,
                timeoutFactory,
                httpClient,
                mapper);
    }
}
