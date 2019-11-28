package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.ext.jdbc.JDBCClient;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.health.ApplicationChecker;
import org.prebid.server.health.DatabaseHealthChecker;
import org.prebid.server.health.GeoLocationHealthChecker;
import org.prebid.server.health.HealthChecker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("status-response")
@ConditionalOnExpression("'${status-response}' != ''")
public class HealthCheckerConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "health-check.database", name = "enabled", havingValue = "true")
    HealthChecker databaseChecker(Vertx vertx,
                                  JDBCClient jdbcClient,
                                  @Value("${health-check.database.refresh-period-ms}") long refreshPeriod) {

        return new DatabaseHealthChecker(vertx, jdbcClient, refreshPeriod);
    }

    @Bean
    @ConditionalOnExpression("${health-check.geolocation.enabled} == true and ${geolocation.enabled} == true")
    HealthChecker geoLocationChecker(Vertx vertx,
                                     @Value("${health-check.geolocation.refresh-period-ms}") long refreshPeriod,
                                     GeoLocationService geoLocationService,
                                     TimeoutFactory timeoutFactory) {

        return new GeoLocationHealthChecker(vertx, refreshPeriod, geoLocationService, timeoutFactory);
    }

    @Bean
    HealthChecker applicationChecker(@Value("${status-response}") String statusResponse) {
        return new ApplicationChecker(statusResponse);
    }
}
