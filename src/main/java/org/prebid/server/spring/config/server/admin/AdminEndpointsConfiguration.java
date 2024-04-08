package org.prebid.server.spring.config.server.admin;

import com.codahale.metrics.MetricRegistry;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.handler.admin.AccountCacheInvalidationHandler;
import org.prebid.server.handler.admin.AdminResourceWrapper;
import org.prebid.server.handler.admin.CollectedMetricsHandler;
import org.prebid.server.handler.admin.CurrencyRatesHandler;
import org.prebid.server.handler.admin.HttpInteractionLogHandler;
import org.prebid.server.handler.admin.LoggerControlKnobHandler;
import org.prebid.server.handler.admin.SettingsCacheNotificationHandler;
import org.prebid.server.handler.admin.TracerLogHandler;
import org.prebid.server.handler.admin.VersionHandler;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.CriteriaManager;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.log.LoggerControlKnob;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.settings.SettingsCache;
import org.prebid.server.util.VersionInfo;
import org.prebid.server.vertx.verticles.server.admin.AdminResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Configuration
public class AdminEndpointsConfiguration {

    @Bean
    @ConditionalOnExpression("${admin-endpoints.version.enabled} == true")
    AdminResource versionEndpoint(
            VersionInfo versionInfo,
            JacksonMapper mapper,
            @Value("${admin-endpoints.version.path}") String path,
            @Value("${admin-endpoints.version.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.version.protected}") boolean isProtected) {

        return new AdminResourceWrapper(
                path,
                isOnApplicationPort,
                isProtected,
                new VersionHandler(versionInfo.getVersion(), versionInfo.getCommitHash(), mapper, path));
    }

    @Bean
    @ConditionalOnExpression("${currency-converter.external-rates.enabled} == true"
            + " and ${admin-endpoints.currency-rates.enabled} == true")
    AdminResource currencyConversionRatesEndpoint(
            CurrencyConversionService currencyConversionRates,
            JacksonMapper mapper,
            @Value("${admin-endpoints.currency-rates.path}") String path,
            @Value("${admin-endpoints.currency-rates.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.currency-rates.protected}") boolean isProtected) {

        return new AdminResourceWrapper(
                path,
                isOnApplicationPort,
                isProtected,
                new CurrencyRatesHandler(currencyConversionRates, path, mapper));
    }

    @Bean
    @ConditionalOnExpression("${settings.in-memory-cache.notification-endpoints-enabled:false}"
            + " and ${admin-endpoints.storedrequest.enabled} == true")
    AdminResource cacheNotificationEndpoint(
            SettingsCache settingsCache,
            JacksonMapper mapper,
            @Value("${admin-endpoints.storedrequest.path}") String path,
            @Value("${admin-endpoints.storedrequest.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.storedrequest.protected}") boolean isProtected) {

        return new AdminResourceWrapper(
                path,
                isOnApplicationPort,
                isProtected,
                new SettingsCacheNotificationHandler(settingsCache, mapper, path));
    }

    @Bean
    @ConditionalOnExpression("${settings.in-memory-cache.notification-endpoints-enabled:false}"
            + " and ${admin-endpoints.storedrequest-amp.enabled} == true")
    AdminResource ampCacheNotificationEndpoint(
            SettingsCache ampSettingsCache,
            JacksonMapper mapper,
            @Value("${admin-endpoints.storedrequest-amp.path}") String path,
            @Value("${admin-endpoints.storedrequest-amp.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.storedrequest-amp.protected}") boolean isProtected) {

        return new AdminResourceWrapper(
                path,
                isOnApplicationPort,
                isProtected,
                new SettingsCacheNotificationHandler(ampSettingsCache, mapper, path));
    }

    @Bean
    @ConditionalOnExpression("${settings.in-memory-cache.notification-endpoints-enabled:false}"
            + " and ${admin-endpoints.cache-invalidation.enabled} == true")
    AdminResource cacheInvalidateNotificationEndpoint(
            CachingApplicationSettings cachingApplicationSettings,
            @Value("${admin-endpoints.cache-invalidation.path}") String path,
            @Value("${admin-endpoints.cache-invalidation.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.cache-invalidation.protected}") boolean isProtected) {

        return new AdminResourceWrapper(
                path,
                isOnApplicationPort,
                isProtected,
                new AccountCacheInvalidationHandler(cachingApplicationSettings, path));
    }

    @Bean
    @ConditionalOnExpression("${admin-endpoints.logging-httpinteraction.enabled} == true")
    AdminResource loggingHttpInteractionEndpoint(
            @Value("${logging.http-interaction.max-limit}") int maxLimit,
            HttpInteractionLogger httpInteractionLogger,
            @Value("${admin-endpoints.logging-httpinteraction.path}") String path,
            @Value("${admin-endpoints.logging-httpinteraction.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.logging-httpinteraction.protected}") boolean isProtected) {

        return new AdminResourceWrapper(
                path,
                isOnApplicationPort,
                isProtected,
                new HttpInteractionLogHandler(maxLimit, httpInteractionLogger, path));
    }

    @Bean
    @ConditionalOnExpression("${admin-endpoints.logging-changelevel.enabled} == true")
    AdminResource loggingChangeLevelEndpoint(
            @Value("${logging.change-level.max-duration-ms}") long maxDuration,
            LoggerControlKnob loggerControlKnob,
            @Value("${admin-endpoints.logging-changelevel.path}") String path,
            @Value("${admin-endpoints.logging-changelevel.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.logging-changelevel.protected}") boolean isProtected) {

        return new AdminResourceWrapper(
                path,
                isOnApplicationPort,
                isProtected,
                new LoggerControlKnobHandler(maxDuration, loggerControlKnob, path));
    }

    @Bean
    @ConditionalOnProperty(prefix = "admin-endpoints.tracelog", name = "enabled", havingValue = "true")
    AdminResource tracerLogEndpoint(
            CriteriaManager criteriaManager,
            @Value("${admin-endpoints.tracelog.path}") String path,
            @Value("${admin-endpoints.tracelog.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.tracelog.protected}") boolean isProtected) {

        return new AdminResourceWrapper(path, isOnApplicationPort, isProtected, new TracerLogHandler(criteriaManager));
    }

    @Bean
    @ConditionalOnExpression("${admin-endpoints.collected-metrics.enabled} == true")
    AdminResource collectedMetricsAdminEndpoint(
            MetricRegistry metricRegistry,
            JacksonMapper mapper,
            @Value("${admin-endpoints.collected-metrics.path}") String path,
            @Value("${admin-endpoints.collected-metrics.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.collected-metrics.protected}") boolean isProtected) {

        return new AdminResourceWrapper(
                path,
                isOnApplicationPort,
                isProtected,
                new CollectedMetricsHandler(metricRegistry, mapper, path));
    }

    @Bean
    AdminResourcesBinder applicationPortAdminResourcesBinder(Map<String, String> adminEndpointCredentials,
                                                             List<AdminResource> resources) {

        final List<AdminResource> applicationPortAdminResources = resources.stream()
                .filter(AdminResource::isOnApplicationPort)
                .toList();

        return new AdminResourcesBinder(adminEndpointCredentials, applicationPortAdminResources);
    }

    @Bean
    AdminResourcesBinder adminPortAdminResourcesBinder(Map<String, String> adminEndpointCredentials,
                                                       List<AdminResource> resources) {

        final List<AdminResource> adminPortAdminResources = resources.stream()
                .filter(Predicate.not(AdminResource::isOnApplicationPort))
                .toList();

        return new AdminResourcesBinder(adminEndpointCredentials, adminPortAdminResources);
    }

    @Bean
    Map<String, String> adminEndpointCredentials(@Autowired(required = false) AdminEndpointCredentials credentials) {
        return ObjectUtils.defaultIfNull(credentials.getCredentials(), Collections.emptyMap());
    }

    @Component
    @ConfigurationProperties(prefix = "admin-endpoints")
    @Data
    @NoArgsConstructor
    public static class AdminEndpointCredentials {

        private Map<String, String> credentials;
    }
}
