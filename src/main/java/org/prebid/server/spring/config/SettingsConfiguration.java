package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.ActivitiesConfigResolver;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.floors.PriceFloorsConfigResolver;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.settings.CompositeApplicationSettings;
import org.prebid.server.settings.DatabaseApplicationSettings;
import org.prebid.server.settings.EnrichingApplicationSettings;
import org.prebid.server.settings.FileApplicationSettings;
import org.prebid.server.settings.HttpApplicationSettings;
import org.prebid.server.settings.S3ApplicationSettings;
import org.prebid.server.settings.SettingsCache;
import org.prebid.server.settings.helper.ParametrizedQueryHelper;
import org.prebid.server.settings.model.Profile;
import org.prebid.server.settings.service.DatabasePeriodicRefreshService;
import org.prebid.server.settings.service.HttpPeriodicRefreshService;
import org.prebid.server.settings.service.S3PeriodicRefreshService;
import org.prebid.server.spring.config.database.DatabaseConfiguration;
import org.prebid.server.vertx.database.DatabaseClient;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@UtilityClass
public class SettingsConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SettingsConfiguration.class);

    @Configuration
    @ConditionalOnProperty(prefix = "settings.filesystem",
            name = {"settings-filename", "stored-requests-dir", "stored-imps-dir"})
    static class FileSettingsConfiguration {

        @Bean
        FileApplicationSettings fileApplicationSettings(
                @Value("${settings.filesystem.settings-filename}") String settingsFileName,
                @Value("${settings.filesystem.stored-requests-dir}") String storedRequestsDir,
                @Value("${settings.filesystem.stored-imps-dir}") String storedImpsDir,
                @Value("${settings.filesystem.profiles-dir}") String profilesDir,
                @Value("${settings.filesystem.stored-responses-dir}") String storedResponsesDir,
                @Value("${settings.filesystem.categories-dir}") String categoriesDir,
                FileSystem fileSystem,
                JacksonMapper jacksonMapper) {

            return new FileApplicationSettings(
                    fileSystem,
                    settingsFileName,
                    storedRequestsDir,
                    storedImpsDir,
                    profilesDir,
                    storedResponsesDir,
                    categoriesDir,
                    jacksonMapper);
        }
    }

    @Configuration
    @ConditionalOnBean(DatabaseConfiguration.class)
    static class DatabaseSettingsConfiguration {

        @Bean
        DatabaseApplicationSettings databaseApplicationSettings(
                @Value("${settings.database.account-query}") String accountQuery,
                @Value("${settings.database.stored-requests-query}") String storedRequestsQuery,
                @Value("${settings.database.amp-stored-requests-query}") String ampStoredRequestsQuery,
                @Value("${settings.database.profiles-query:#{null}}") String profilesQuery,
                @Value("${settings.database.stored-responses-query}") String storedResponsesQuery,
                ParametrizedQueryHelper parametrizedQueryHelper,
                DatabaseClient databaseClient,
                JacksonMapper jacksonMapper) {

            return new DatabaseApplicationSettings(
                    databaseClient,
                    jacksonMapper,
                    parametrizedQueryHelper,
                    accountQuery,
                    storedRequestsQuery,
                    ampStoredRequestsQuery,
                    profilesQuery,
                    storedResponsesQuery);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "settings.http", name = {"endpoint", "amp-endpoint"})
    static class HttpSettingsConfiguration {

        @Bean
        HttpApplicationSettings httpApplicationSettings(
                @Value("${settings.http.rfc3986-compatible:false}") boolean isRfc3986Compatible,
                HttpClient httpClient,
                JacksonMapper mapper,
                @Value("${settings.http.endpoint}") String endpoint,
                @Value("${settings.http.amp-endpoint}") String ampEndpoint,
                @Value("${settings.http.video-endpoint}") String videoEndpoint,
                @Value("${settings.http.category-endpoint}") String categoryEndpoint) {

            return new HttpApplicationSettings(
                    isRfc3986Compatible,
                    endpoint,
                    ampEndpoint,
                    videoEndpoint,
                    categoryEndpoint,
                    httpClient,
                    mapper);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "settings.in-memory-cache.http-update",
            name = {"endpoint", "amp-endpoint", "refresh-rate", "timeout"})
    static class HttpPeriodicRefreshServiceConfiguration {

        @Value("${settings.in-memory-cache.http-update.refresh-rate}")
        long refreshPeriod;

        @Value("${settings.in-memory-cache.http-update.timeout}")
        long timeout;

        @Autowired
        Vertx vertx;

        @Autowired
        HttpClient httpClient;

        @Bean
        public HttpPeriodicRefreshService httpPeriodicRefreshService(
                @Value("${settings.in-memory-cache.http-update.endpoint}") String endpoint,
                SettingsCache<String> settingsCache,
                JacksonMapper mapper) {

            return new HttpPeriodicRefreshService(
                    endpoint, refreshPeriod, timeout, settingsCache, vertx, httpClient, mapper);
        }

        @Bean
        public HttpPeriodicRefreshService ampHttpPeriodicRefreshService(
                @Value("${settings.in-memory-cache.http-update.amp-endpoint}") String ampEndpoint,
                SettingsCache<String> ampSettingsCache,
                JacksonMapper mapper) {

            return new HttpPeriodicRefreshService(
                    ampEndpoint, refreshPeriod, timeout, ampSettingsCache, vertx, httpClient, mapper);
        }
    }

    @Configuration
    @ConditionalOnProperty(
            prefix = "settings.in-memory-cache.database-update",
            name = {"refresh-rate", "timeout", "init-query", "update-query", "amp-init-query", "amp-update-query"})
    static class DatabasePeriodicRefreshServiceConfiguration {

        @Value("${settings.in-memory-cache.database-update.refresh-rate}")
        long refreshPeriod;

        @Value("${settings.in-memory-cache.database-update.timeout}")
        long timeout;

        @Autowired
        Vertx vertx;

        @Autowired
        DatabaseClient databaseClient;

        @Autowired
        TimeoutFactory timeoutFactory;

        @Autowired
        Metrics metrics;

        @Autowired
        Clock clock;

        @Bean
        public DatabasePeriodicRefreshService databasePeriodicRefreshService(
                @Qualifier("settingsCache") SettingsCache<String> settingsCache,
                @Value("${settings.in-memory-cache.database-update.init-query}") String initQuery,
                @Value("${settings.in-memory-cache.database-update.update-query}") String updateQuery) {

            return new DatabasePeriodicRefreshService(
                    initQuery,
                    updateQuery,
                    refreshPeriod,
                    timeout,
                    MetricName.stored_request,
                    settingsCache,
                    vertx,
                    databaseClient,
                    timeoutFactory,
                    metrics,
                    clock);
        }

        @Bean
        public DatabasePeriodicRefreshService ampDatabasePeriodicRefreshService(
                @Qualifier("ampSettingsCache") SettingsCache<String> ampSettingsCache,
                @Value("${settings.in-memory-cache.database-update.amp-init-query}") String ampInitQuery,
                @Value("${settings.in-memory-cache.database-update.amp-update-query}") String ampUpdateQuery) {

            return new DatabasePeriodicRefreshService(
                    ampInitQuery,
                    ampUpdateQuery,
                    refreshPeriod,
                    timeout,
                    MetricName.amp_stored_request,
                    ampSettingsCache,
                    vertx,
                    databaseClient,
                    timeoutFactory,
                    metrics,
                    clock);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "settings.s3", name = {"accounts-dir", "stored-imps-dir", "stored-requests-dir"})
    static class S3SettingsConfiguration {

        @Component
        @ConfigurationProperties(prefix = "settings.s3")
        @Validated
        @Data
        @NoArgsConstructor
        protected static class S3ConfigurationProperties {

            /**
             * If accessKeyId and secretAccessKey are provided in the
             * configuration file then they will be used. Otherwise, the
             * DefaultCredentialsProvider will look for credentials in this order:
             * <p>
             * - Java System Properties
             * - Environment Variables
             * - Web Identity Token
             * - AWS credentials file (~/.aws/credentials)
             * - ECS container credentials
             * - EC2 instance profile
             */
            private String accessKeyId;
            private String secretAccessKey;

            private boolean useStaticCredentials() {
                return StringUtils.isNotBlank(accessKeyId) && StringUtils.isNotBlank(secretAccessKey);
            }

            /**
             * If not provided AWS_GLOBAL will be used as a region
             */
            private String region;

            @NotBlank
            private String endpoint;

            @NotBlank
            private String bucket;

            @NotBlank
            private Boolean forcePathStyle;

            @NotBlank
            private String accountsDir;

            @NotBlank
            private String storedImpsDir;

            @NotBlank
            private String storedRequestsDir;

            @NotBlank
            private String storedResponsesDir;
        }

        @Bean
        S3AsyncClient s3AsyncClient(S3ConfigurationProperties s3ConfigurationProperties) throws URISyntaxException {
            final Region awsRegion = Optional.ofNullable(s3ConfigurationProperties.getRegion())
                    .map(Region::of)
                    .orElse(Region.AWS_GLOBAL);

            return S3AsyncClient.builder()
                    .credentialsProvider(awsCredentialsProvider(s3ConfigurationProperties))
                    .endpointOverride(new URI(s3ConfigurationProperties.getEndpoint()))
                    .forcePathStyle(s3ConfigurationProperties.getForcePathStyle())
                    .region(awsRegion)
                    .build();
        }

        private static AwsCredentialsProvider awsCredentialsProvider(S3ConfigurationProperties config) {
            final AwsCredentialsProvider credentialsProvider = config.useStaticCredentials()
                    ? StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey()))
                    : DefaultCredentialsProvider.create();

            try {
                credentialsProvider.resolveCredentials();
            } catch (SdkClientException e) {
                logger.error("Failed to resolve AWS credentials", e);
            }

            return credentialsProvider;
        }

        @Bean
        S3ApplicationSettings s3ApplicationSettings(S3AsyncClient s3AsyncClient,
                                                    S3ConfigurationProperties s3ConfigurationProperties,
                                                    JacksonMapper mapper,
                                                    Vertx vertx) {

            return new S3ApplicationSettings(
                    s3AsyncClient,
                    s3ConfigurationProperties.getBucket(),
                    s3ConfigurationProperties.getAccountsDir(),
                    s3ConfigurationProperties.getStoredImpsDir(),
                    s3ConfigurationProperties.getStoredRequestsDir(),
                    s3ConfigurationProperties.getStoredResponsesDir(),
                    mapper,
                    vertx);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "settings.in-memory-cache.s3-update", name = {"refresh-rate", "timeout"})
    static class S3PeriodicRefreshServiceConfiguration {

        @Bean
        public S3PeriodicRefreshService s3PeriodicRefreshService(
                S3AsyncClient s3AsyncClient,
                S3SettingsConfiguration.S3ConfigurationProperties s3ConfigurationProperties,
                @Value("${settings.in-memory-cache.s3-update.refresh-rate}") long refreshPeriod,
                SettingsCache<String> settingsCache,
                Clock clock,
                Metrics metrics,
                Vertx vertx) {

            return new S3PeriodicRefreshService(
                    s3AsyncClient,
                    s3ConfigurationProperties.getBucket(),
                    s3ConfigurationProperties.getStoredRequestsDir(),
                    s3ConfigurationProperties.getStoredImpsDir(),
                    refreshPeriod,
                    settingsCache,
                    MetricName.stored_request,
                    clock,
                    metrics,
                    vertx);
        }
    }

    /**
     * This configuration defines a collection of application settings fetchers and its ordering.
     */
    @Configuration
    static class CompositeSettingsConfiguration {

        @Bean
        CompositeApplicationSettings compositeApplicationSettings(
                @Autowired(required = false) FileApplicationSettings fileApplicationSettings,
                @Autowired(required = false) DatabaseApplicationSettings databaseApplicationSettings,
                @Autowired(required = false) HttpApplicationSettings httpApplicationSettings,
                @Autowired(required = false) S3ApplicationSettings s3ApplicationSettings) {

            final List<ApplicationSettings> applicationSettingsList = Stream.of(
                            fileApplicationSettings,
                            databaseApplicationSettings,
                            s3ApplicationSettings,
                            httpApplicationSettings)
                    .filter(Objects::nonNull)
                    .toList();

            return new CompositeApplicationSettings(applicationSettingsList);
        }
    }

    @Configuration
    static class EnrichingSettingsConfiguration {

        @Bean
        EnrichingApplicationSettings enrichingApplicationSettings(
                @Value("${settings.enforce-valid-account}") boolean enforceValidAccount,
                @Value("${settings.default-account-config:#{null}}") String defaultAccountConfig,
                JacksonMapper mapper,
                CompositeApplicationSettings compositeApplicationSettings,
                PriceFloorsConfigResolver priceFloorsConfigResolver,
                ActivitiesConfigResolver activitiesConfigResolver,
                JsonMerger jsonMerger) {

            return new EnrichingApplicationSettings(
                    enforceValidAccount,
                    defaultAccountConfig,
                    compositeApplicationSettings,
                    priceFloorsConfigResolver,
                    activitiesConfigResolver,
                    jsonMerger,
                    mapper);
        }
    }

    @Configuration
    static class CachingSettingsConfiguration {

        @Bean
        @ConditionalOnProperty(prefix = "settings.in-memory-cache", name = {"ttl-seconds", "cache-size"})
        CachingApplicationSettings cachingApplicationSettings(
                EnrichingApplicationSettings enrichingApplicationSettings,
                ApplicationSettingsCacheProperties cacheProperties,
                @Qualifier("settingsCache") SettingsCache<String> cache,
                @Qualifier("ampSettingsCache") SettingsCache<String> ampCache,
                @Qualifier("videoSettingCache") SettingsCache<String> videoCache,
                @Qualifier("profileSettingCache") SettingsCache<Profile> profilesCache,
                Metrics metrics) {

            return new CachingApplicationSettings(
                    enrichingApplicationSettings,
                    cache,
                    ampCache,
                    videoCache,
                    profilesCache,
                    metrics,
                    cacheProperties.getTtlSeconds(),
                    cacheProperties.getCacheSize(),
                    cacheProperties.getJitterSeconds());
        }
    }

    @Configuration
    static class ApplicationSettingsConfiguration {

        @Bean
        ApplicationSettings applicationSettings(
                @Autowired(required = false) CachingApplicationSettings cachingApplicationSettings,
                EnrichingApplicationSettings enrichingApplicationSettings) {
            return ObjectUtils.defaultIfNull(cachingApplicationSettings, enrichingApplicationSettings);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "settings.in-memory-cache", name = {"ttl-seconds", "cache-size"})
    static class CacheConfiguration {

        @Bean
        @Qualifier("settingsCache")
        SettingsCache<String> settingsCache(ApplicationSettingsCacheProperties cacheProperties) {
            return new SettingsCache<>(
                    cacheProperties.getTtlSeconds(),
                    cacheProperties.getCacheSize(),
                    cacheProperties.getJitterSeconds());
        }

        @Bean
        @Qualifier("ampSettingsCache")
        SettingsCache<String> ampSettingsCache(ApplicationSettingsCacheProperties cacheProperties) {
            return new SettingsCache<>(
                    cacheProperties.getTtlSeconds(),
                    cacheProperties.getCacheSize(),
                    cacheProperties.getJitterSeconds());
        }

        @Bean
        @Qualifier("videoSettingCache")
        SettingsCache<String> videoSettingCache(ApplicationSettingsCacheProperties cacheProperties) {
            return new SettingsCache<>(
                    cacheProperties.getTtlSeconds(),
                    cacheProperties.getCacheSize(),
                    cacheProperties.getJitterSeconds());
        }

        @Bean
        @Qualifier("profileSettingCache")
        SettingsCache<Profile> profileSettingCache(ApplicationSettingsCacheProperties cacheProperties) {
            return new SettingsCache<>(
                    cacheProperties.getTtlSeconds(),
                    cacheProperties.getCacheSize(),
                    cacheProperties.getJitterSeconds());
        }
    }

    @Component
    @ConfigurationProperties(prefix = "settings.in-memory-cache")
    @ConditionalOnProperty(prefix = "settings.in-memory-cache", name = {"ttl-seconds", "cache-size"})
    @Validated
    @Data
    @NoArgsConstructor
    protected static class ApplicationSettingsCacheProperties {

        @NotNull
        @Min(1)
        private Integer ttlSeconds;
        @NotNull
        @Min(1)
        private Integer cacheSize;
        @Min(0)
        private int jitterSeconds;
    }
}
