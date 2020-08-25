package org.prebid.server.spring.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.handler.AccountCacheInvalidationHandler;
import org.prebid.server.handler.AdminHandler;
import org.prebid.server.handler.CurrencyRatesHandler;
import org.prebid.server.handler.CustomizedAdminEndpoint;
import org.prebid.server.handler.SettingsCacheNotificationHandler;
import org.prebid.server.handler.VersionHandler;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.manager.AdminManager;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.settings.SettingsCache;
import org.prebid.server.util.VersionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
                VersionHandler.create(versionInfo.getVersion(), versionInfo.getCommitHash(), mapper),
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
                new CurrencyRatesHandler(currencyConversionRates, mapper),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnExpression("${settings.in-memory-cache.notification-endpoints-enabled} == true"
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
                new SettingsCacheNotificationHandler(settingsCache, mapper),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnExpression("${settings.in-memory-cache.notification-endpoints-enabled} == true"
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
                new SettingsCacheNotificationHandler(ampSettingsCache, mapper),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnExpression("${settings.in-memory-cache.notification-endpoints-enabled} == true"
            + " and ${admin-endpoints.cache-invalidation.enabled} == true")
    CustomizedAdminEndpoint cacheInvalidateNotificationEndpoint(
            CachingApplicationSettings cachingApplicationSettings,
            @Value("${admin-endpoints.cache-invalidation.path}") String path,
            @Value("${admin-endpoints.cache-invalidation.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.cache-invalidation.protected}") boolean isProtected,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials) {

        return new CustomizedAdminEndpoint(
                path,
                new AccountCacheInvalidationHandler(cachingApplicationSettings),
                isOnApplicationPort,
                isProtected)
                .withCredentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnExpression("${logger-level-modifier.enabled} == true"
            + " and ${admin-endpoints.logger-level-modifier.enabled} == true")
    CustomizedAdminEndpoint loggerLevelModifierEndpoint(
            AdminManager adminManager,
            @Value("${admin-endpoints.logger-level-modifier.path}") String path,
            @Value("${admin-endpoints.logger-level-modifier.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.logger-level-modifier.protected}") boolean isProtected,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials) {

        return new CustomizedAdminEndpoint(
                path,
                new AdminHandler(adminManager),
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
