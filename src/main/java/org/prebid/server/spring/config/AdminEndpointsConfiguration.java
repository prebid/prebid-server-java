package org.prebid.server.spring.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.execution.LogModifier;
import org.prebid.server.handler.AdminHandler;
import org.prebid.server.handler.CurrencyRatesHandler;
import org.prebid.server.handler.CustomizedAdminEndpoint;
import org.prebid.server.handler.SettingsCacheNotificationHandler;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.SettingsCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Configuration
public class AdminEndpointsConfiguration {

    private static final String LOGGER_LEVEL_ENDPOINT = "/pbs-admin/admin";
    private static final String CURRENCY_RATES_ENDPOINT = "/pbs-admin/currency-rates";
    private static final String STOREDREQUESTS_OPENRTB_ENDPOINT = "/pbs-admin/storedrequests/openrtb2";
    private static final String STOREDREQUESTS_AMP_ENDPOINT = "/pbs-admin/storedrequests/amp";

    @Bean
    @ConditionalOnProperty(prefix = "logger-level-modifier", name = "enabled", havingValue = "true")
    CustomizedAdminEndpoint loggerLevelModifierEndpoint(
            LogModifier logModifier,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials,
            @Value("${admin-endpoints.logger-level-modifier.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.logger-level-modifier.protected}") boolean isProtected) {
        final AdminHandler adminHandler = new AdminHandler(logModifier);
        return new CustomizedAdminEndpoint(LOGGER_LEVEL_ENDPOINT, adminHandler, isOnApplicationPort,
                isProtected)
                .credentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnProperty(prefix = "currency-converter.external-rates", name = "enabled", havingValue = "true")
    CustomizedAdminEndpoint currencyConversionRatesEndpoint(
            CurrencyConversionService currencyConversionRates,
            JacksonMapper mapper,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials,
            @Value("${admin-endpoints.currency-rates.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.currency-rates.protected}") boolean isProtected) {
        final CurrencyRatesHandler currencyRatesHandler = new CurrencyRatesHandler(currencyConversionRates, mapper);
        return new CustomizedAdminEndpoint(CURRENCY_RATES_ENDPOINT, currencyRatesHandler, isOnApplicationPort,
                isProtected)
                .credentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnProperty(prefix = "settings.in-memory-cache", name = "notification-endpoints-enabled",
            havingValue = "true")
    CustomizedAdminEndpoint cacheNotificationEndpoint(
            SettingsCache settingsCache,
            JacksonMapper mapper,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials,
            @Value("${admin-endpoints.storedrequest.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.storedrequest.protected}") boolean isProtected) {
        final SettingsCacheNotificationHandler cacheNotificationHandler =
                new SettingsCacheNotificationHandler(settingsCache, mapper);
        return new CustomizedAdminEndpoint(STOREDREQUESTS_OPENRTB_ENDPOINT, cacheNotificationHandler,
                isOnApplicationPort, isProtected)
                .credentials(adminEndpointCredentials);
    }

    @Bean
    @ConditionalOnProperty(prefix = "settings.in-memory-cache", name = "notification-endpoints-enabled",
            havingValue = "true")
    CustomizedAdminEndpoint ampCacheNotificationEndpoint(
            SettingsCache ampSettingsCache,
            JacksonMapper mapper,
            @Autowired(required = false) Map<String, String> adminEndpointCredentials,
            @Value("${admin-endpoints.storedrequest-amp.on-application-port}") boolean isOnApplicationPort,
            @Value("${admin-endpoints.storedrequest-amp.protected}") boolean isProtected) {
        final SettingsCacheNotificationHandler settingsCacheNotificationHandler =
                new SettingsCacheNotificationHandler(ampSettingsCache, mapper);
        return new CustomizedAdminEndpoint(STOREDREQUESTS_AMP_ENDPOINT, settingsCacheNotificationHandler,
                isOnApplicationPort, isProtected)
                .credentials(adminEndpointCredentials);
    }

    @Bean
    Map<String, String> adminEndpointCredentials(
            @Autowired(required = false) AdminEndpointCredentials adminEndpointCredentials) {
        return adminEndpointCredentials == null
                ? Collections.emptyMap()
                : adminEndpointCredentials.getCredentials();
    }

    @Component
    @ConfigurationProperties(prefix = "admin-endpoints")
    @Data
    @NoArgsConstructor
    public static class AdminEndpointCredentials {
        private Map<String, String> credentials;
    }
}
