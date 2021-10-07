package org.prebid.server.spring.config;

import com.codahale.metrics.MetricRegistry;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.deals.DeliveryProgressService;
import org.prebid.server.deals.simulation.DealsSimulationAdminHandler;
import org.prebid.server.handler.AccountCacheInvalidationHandler;
import org.prebid.server.handler.CollectedMetricsHandler;
import org.prebid.server.handler.CurrencyRatesHandler;
import org.prebid.server.handler.CustomizedAdminEndpoint;
import org.prebid.server.handler.DealsStatusHandler;
import org.prebid.server.handler.HttpInteractionLogHandler;
import org.prebid.server.handler.LineItemStatusHandler;
import org.prebid.server.handler.LoggerControlKnobHandler;
import org.prebid.server.handler.SettingsCacheNotificationHandler;
import org.prebid.server.handler.TracerLogHandler;
import org.prebid.server.handler.VersionHandler;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.CriteriaManager;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.log.LoggerControlKnob;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.settings.SettingsCache;
import org.prebid.server.util.VersionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Configuration
public class AdminEndpointsConfiguration {

    @Bean
    @ConditionalOnExpression("${admin-endpoints.version.enabled} == true")
    CustomizedAdminEndpoint versionEndpoint(
            VersionInfo versionInfo,
            JacksonMapper mapper,
            @Value("${admin-endpoints.version.path}") String path,
            @Value("${admin-endpoints.version.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.version.protected}") boolean isProtected,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials) {

        return new CustomizedAdminEndpoint(
                path,
                new VersionHandler(versionInfo.getVersion(), versionInfo.getCommitHash(), mapper, path),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnExpression("${currency-converter.external-rates.enabled} == true"
            + " and ${admin-endpoints.currency-rates.enabled} == true")
    CustomizedAdminEndpoint currencyConversionRatesEndpoint(
            CurrencyConversionService currencyConversionRates,
            JacksonMapper mapper,
            @Value("${admin-endpoints.currency-rates.path}") String path,
            @Value("${admin-endpoints.currency-rates.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.currency-rates.protected}") boolean isProtected,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials) {

        return new CustomizedAdminEndpoint(
                path,
                new CurrencyRatesHandler(currencyConversionRates, path, mapper),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnExpression("${settings.in-memory-cache.notification-endpoints-enabled:false}"
            + " and ${admin-endpoints.storedrequest.enabled} == true")
    CustomizedAdminEndpoint cacheNotificationEndpoint(
            SettingsCache settingsCache,
            JacksonMapper mapper,
            @Value("${admin-endpoints.storedrequest.path}") String path,
            @Value("${admin-endpoints.storedrequest.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.storedrequest.protected}") boolean isProtected,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials) {

        return new CustomizedAdminEndpoint(
                path,
                new SettingsCacheNotificationHandler(settingsCache, mapper, path),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnExpression("${settings.in-memory-cache.notification-endpoints-enabled:false}"
            + " and ${admin-endpoints.storedrequest-amp.enabled} == true")
    CustomizedAdminEndpoint ampCacheNotificationEndpoint(
            SettingsCache ampSettingsCache,
            JacksonMapper mapper,
            @Value("${admin-endpoints.storedrequest-amp.path}") String path,
            @Value("${admin-endpoints.storedrequest-amp.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.storedrequest-amp.protected}") boolean isProtected,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials) {

        return new CustomizedAdminEndpoint(
                path,
                new SettingsCacheNotificationHandler(ampSettingsCache, mapper, path),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnExpression("${settings.in-memory-cache.notification-endpoints-enabled:false}"
            + " and ${admin-endpoints.cache-invalidation.enabled} == true")
    CustomizedAdminEndpoint cacheInvalidateNotificationEndpoint(
            CachingApplicationSettings cachingApplicationSettings,
            @Value("${admin-endpoints.cache-invalidation.path}") String path,
            @Value("${admin-endpoints.cache-invalidation.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.cache-invalidation.protected}") boolean isProtected,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials) {

        return new CustomizedAdminEndpoint(
                path,
                new AccountCacheInvalidationHandler(cachingApplicationSettings, path),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnExpression("${admin-endpoints.logging-httpinteraction.enabled} == true")
    CustomizedAdminEndpoint loggingHttpInteractionEndpoint(
            @Value("${logging.http-interaction.max-limit}") int maxLimit,
            HttpInteractionLogger httpInteractionLogger,
            @Value("${admin-endpoints.logging-httpinteraction.path}") String path,
            @Value("${admin-endpoints.logging-httpinteraction.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.logging-httpinteraction.protected}") boolean isProtected,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials) {

        return new CustomizedAdminEndpoint(
                path,
                new HttpInteractionLogHandler(maxLimit, httpInteractionLogger, path),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnExpression("${admin-endpoints.logging-changelevel.enabled} == true")
    CustomizedAdminEndpoint loggingChangeLevelEndpoint(
            @Value("${logging.change-level.max-duration-ms}") long maxDuration,
            LoggerControlKnob loggerControlKnob,
            @Value("${admin-endpoints.logging-changelevel.path}") String path,
            @Value("${admin-endpoints.logging-changelevel.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.logging-changelevel.protected}") boolean isProtected,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials) {

        return new CustomizedAdminEndpoint(
                path,
                new LoggerControlKnobHandler(maxDuration, loggerControlKnob, path),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnProperty(prefix = "admin-endpoints.tracelog", name = "enabled", havingValue = "true")
    CustomizedAdminEndpoint tracerLogEndpoint(
            CriteriaManager criteriaManager,
            @Value("${admin-endpoints.tracelog.path}") String path,
            @Value("${admin-endpoints.tracelog.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.tracelog.protected}") boolean isProtected,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials) {

        return new CustomizedAdminEndpoint(
                path,
                new TracerLogHandler(criteriaManager),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnExpression("${deals.enabled} == true and ${admin-endpoints.deals-status.enabled} == true")
    CustomizedAdminEndpoint dealsStatusEndpoint(
            DeliveryProgressService deliveryProgressService,
            JacksonMapper mapper,
            @Value("${admin-endpoints.deals-status.path}") String path,
            @Value("${admin-endpoints.deals-status.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.deals-status.protected}") boolean isProtected,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials) {

        return new CustomizedAdminEndpoint(
                path,
                new DealsStatusHandler(deliveryProgressService, mapper),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnExpression("${deals.enabled} == true and ${admin-endpoints.lineitem-status.enabled} == true")
    CustomizedAdminEndpoint lineItemStatusEndpoint(
            DeliveryProgressService deliveryProgressService,
            JacksonMapper mapper,
            @Value("${admin-endpoints.lineitem-status.path}") String path,
            @Value("${admin-endpoints.lineitem-status.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.lineitem-status.protected}") boolean isProtected,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials) {

        return new CustomizedAdminEndpoint(
                path,
                new LineItemStatusHandler(deliveryProgressService, mapper, path),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnExpression("${deals.enabled} == true and ${deals.simulation.enabled} == true"
            + " and ${admin-endpoints.e2eadmin.enabled} == true")
    CustomizedAdminEndpoint dealsSimulationAdminEndpoint(
            DealsSimulationAdminHandler dealsSimulationAdminHandler,
            @Value("${admin-endpoints.e2eadmin.path}") String path,
            @Value("${admin-endpoints.e2eadmin.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.e2eadmin.protected}") boolean isProtected,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials) {

        return new CustomizedAdminEndpoint(
                path,
                dealsSimulationAdminHandler,
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnExpression("${admin-endpoints.collected-metrics.enabled} == true")
    CustomizedAdminEndpoint collectedMetricsAdminEndpoint(
            MetricRegistry metricRegistry,
            JacksonMapper mapper,
            @Value("${admin-endpoints.collected-metrics.path}") String path,
            @Value("${admin-endpoints.collected-metrics.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.collected-metrics.protected}") boolean isProtected,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials) {

        return new CustomizedAdminEndpoint(
                path,
                new CollectedMetricsHandler(metricRegistry, mapper, path),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    Map<String, String> adminEndpointCredentials(
            @Autowired(required = false) AdminEndpointCredentials adminEndpointCredentials) {

        return ObjectUtils.defaultIfNull(adminEndpointCredentials.getCredentials(), Collections.emptyMap());
    }

    @Component
    @ConfigurationProperties(prefix = "admin-endpoints")
    @Data
    @NoArgsConstructor
    public static class AdminEndpointCredentials {

        private Map<String, String> credentials;
    }
}
