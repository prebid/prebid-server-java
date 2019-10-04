package org.prebid.server.spring.config;

import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.jdbc.JDBCClient;
import org.prebid.server.auction.AmpRequestFactory;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.AuctionRequestFactory;
import org.prebid.server.auction.BidResponseCreator;
import org.prebid.server.auction.BidResponsePostProcessor;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.InterstitialProcessor;
import org.prebid.server.auction.PreBidRequestContextFactory;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.StoredResponseProcessor;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.events.EventsService;
import org.prebid.server.execution.RemoteFileSyncer;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.vendorlist.VendorListService;
import org.prebid.server.geolocation.CircuitBreakerSecuredGeoLocationService;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.MaxMindGeoLocationService;
import org.prebid.server.health.ApplicationChecker;
import org.prebid.server.health.DatabaseHealthChecker;
import org.prebid.server.health.HealthChecker;
import org.prebid.server.metric.Metrics;
import org.prebid.server.optout.GoogleRecaptchaVerifier;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.spring.config.model.CircuitBreakerProperties;
import org.prebid.server.spring.config.model.HttpClientProperties;
import org.prebid.server.spring.config.model.RemoteFileSyncerProperties;
import org.prebid.server.validation.BidderParamValidator;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.vertx.http.BasicHttpClient;
import org.prebid.server.vertx.http.CircuitBreakerSecuredHttpClient;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

import javax.validation.constraints.Min;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class ServiceConfiguration {

    @Bean
    CacheService cacheService(
            @Value("${cache.scheme}") String scheme,
            @Value("${cache.host}") String host,
            @Value("${cache.path}") String path,
            @Value("${cache.query}") String query,
            @Value("${cache.banner-ttl-seconds:#{null}}") Integer bannerCacheTtl,
            @Value("${cache.video-ttl-seconds:#{null}}") Integer videoCacheTtl,
            @Value("${external-url}") String externalUrl,
            EventsService eventsService,
            HttpClient httpClient,
            Clock clock) {

        return new CacheService(
                CacheTtl.of(bannerCacheTtl, videoCacheTtl),
                httpClient,
                CacheService.getCacheEndpointUrl(scheme, host, path),
                CacheService.getCachedAssetUrlTemplate(scheme, host, path, query),
                eventsService,
                clock);
    }

    @Bean
    ImplicitParametersExtractor implicitParametersExtractor(PublicSuffixList psl) {
        return new ImplicitParametersExtractor(psl);
    }

    @Bean
    TimeoutResolver timeoutResolver(
            @Value("${default-timeout-ms}") long defaultTimeout,
            @Value("${max-timeout-ms}") long maxTimeout,
            @Value("${timeout-adjustment-ms}") long timeoutAdjustment) {

        return new TimeoutResolver(defaultTimeout, maxTimeout, timeoutAdjustment);
    }

    @Bean
    TimeoutResolver auctionTimeoutResolver(
            @Value("${auction.default-timeout-ms}") long defaultTimeout,
            @Value("${auction.max-timeout-ms}") long maxTimeout,
            @Value("${auction.timeout-adjustment-ms}") long timeoutAdjustment) {

        return new TimeoutResolver(defaultTimeout, maxTimeout, timeoutAdjustment);
    }

    @Bean
    TimeoutResolver ampTimeoutResolver(
            @Value("${amp.default-timeout-ms}") long defaultTimeout,
            @Value("${amp.max-timeout-ms}") long maxTimeout,
            @Value("${amp.timeout-adjustment-ms}") long timeoutAdjustment) {

        return new TimeoutResolver(defaultTimeout, maxTimeout, timeoutAdjustment);
    }

    @Bean
    PreBidRequestContextFactory preBidRequestContextFactory(
            TimeoutResolver timeoutResolver,
            ImplicitParametersExtractor implicitParametersExtractor,
            ApplicationSettings applicationSettings,
            UidsCookieService uidsCookieService,
            TimeoutFactory timeoutFactory) {

        return new PreBidRequestContextFactory(timeoutResolver, implicitParametersExtractor, applicationSettings,
                uidsCookieService, timeoutFactory);
    }

    @Bean
    AuctionRequestFactory auctionRequestFactory(
            @Value("${auction.max-request-size}") @Min(0) int maxRequestSize,
            @Value("${settings.enforce-valid-account}") boolean enforceValidAccount,
            @Value("${auction.ad-server-currency:#{null}}") String adServerCurrency,
            @Value("${auction.blacklisted-accounts}") String blacklistedAccountsString,
            StoredRequestProcessor storedRequestProcessor,
            ImplicitParametersExtractor implicitParametersExtractor,
            UidsCookieService uidsCookieService,
            BidderCatalog bidderCatalog,
            RequestValidator requestValidator,
            TimeoutResolver timeoutResolver,
            TimeoutFactory timeoutFactory,
            ApplicationSettings applicationSettings) {

        final List<String> blacklistedAccounts = Stream.of(blacklistedAccountsString.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
        return new AuctionRequestFactory(maxRequestSize, enforceValidAccount, adServerCurrency, blacklistedAccounts,
                storedRequestProcessor, implicitParametersExtractor, uidsCookieService, bidderCatalog, requestValidator,
                new InterstitialProcessor(), timeoutResolver, timeoutFactory, applicationSettings);
    }

    @Bean
    AmpRequestFactory ampRequestFactory(
            StoredRequestProcessor storedRequestProcessor,
            AuctionRequestFactory auctionRequestFactory,
            TimeoutResolver timeoutResolver) {

        return new AmpRequestFactory(storedRequestProcessor, auctionRequestFactory, timeoutResolver);
    }

    @Bean
    GoogleRecaptchaVerifier googleRecaptchaVerifier(
            @Value("${recaptcha-url}") String recaptchaUrl,
            @Value("${recaptcha-secret}") String recaptchaSecret,
            HttpClient httpClient) {

        return new GoogleRecaptchaVerifier(httpClient, recaptchaUrl, recaptchaSecret);
    }

    @Bean
    @ConfigurationProperties(prefix = "http-client")
    HttpClientProperties httpClientProperties() {
        return new HttpClientProperties();
    }

    @Bean
    @Scope(scopeName = VertxContextScope.NAME, proxyMode = ScopedProxyMode.INTERFACES)
    @ConditionalOnProperty(prefix = "http-client.circuit-breaker", name = "enabled", havingValue = "false",
            matchIfMissing = true)
    BasicHttpClient basicHttpClient(Vertx vertx, HttpClientProperties httpClientProperties) {

        return createBasicHttpClient(vertx, httpClientProperties.getMaxPoolSize(),
                httpClientProperties.getConnectTimeoutMs(), httpClientProperties.getUseCompression(),
                httpClientProperties.getMaxRedirects());
    }

    @Bean
    @ConfigurationProperties(prefix = "http-client.circuit-breaker")
    @ConditionalOnProperty(prefix = "http-client.circuit-breaker", name = "enabled", havingValue = "true")
    CircuitBreakerProperties httpClientCircuitBreakerProperties() {
        return new CircuitBreakerProperties();
    }

    @Bean
    @Scope(scopeName = VertxContextScope.NAME, proxyMode = ScopedProxyMode.INTERFACES)
    @ConditionalOnProperty(prefix = "http-client.circuit-breaker", name = "enabled", havingValue = "true")
    CircuitBreakerSecuredHttpClient circuitBreakerSecuredHttpClient(
            Vertx vertx,
            Metrics metrics,
            HttpClientProperties httpClientProperties,
            @Qualifier("httpClientCircuitBreakerProperties") CircuitBreakerProperties circuitBreakerProperties,
            Clock clock) {

        final HttpClient httpClient = createBasicHttpClient(vertx, httpClientProperties.getMaxPoolSize(),
                httpClientProperties.getConnectTimeoutMs(), httpClientProperties.getUseCompression(),
                httpClientProperties.getMaxRedirects());
        return new CircuitBreakerSecuredHttpClient(vertx, httpClient, metrics,
                circuitBreakerProperties.getOpeningThreshold(), circuitBreakerProperties.getOpeningIntervalMs(),
                circuitBreakerProperties.getClosingIntervalMs(), clock);
    }

    private static BasicHttpClient createBasicHttpClient(Vertx vertx, int maxPoolSize, int connectTimeoutMs,
                                                         boolean useCompression, int maxRedirects) {
        final HttpClientOptions options = new HttpClientOptions()
                .setMaxPoolSize(maxPoolSize)
                .setTryUseCompression(useCompression)
                .setConnectTimeout(connectTimeoutMs)
                // Vert.x's HttpClientRequest needs this value to be 2 for redirections to be followed once,
                // 3 for twice, and so on
                .setMaxRedirects(maxRedirects + 1);
        return new BasicHttpClient(vertx, vertx.createHttpClient(options));
    }

    @Bean
    UidsCookieService uidsCookieService(
            @Value("${host-cookie.optout-cookie.name:#{null}}") String optOutCookieName,
            @Value("${host-cookie.optout-cookie.value:#{null}}") String optOutCookieValue,
            @Value("${host-cookie.family:#{null}}") String hostCookieFamily,
            @Value("${host-cookie.cookie-name:#{null}}") String hostCookieName,
            @Value("${host-cookie.domain:#{null}}") String hostCookieDomain,
            @Value("${host-cookie.ttl-days}") Integer ttlDays) {

        return new UidsCookieService(optOutCookieName, optOutCookieValue, hostCookieFamily, hostCookieName,
                hostCookieDomain, ttlDays);
    }

    @Bean
    VendorListService vendorListService(
            FileSystem fileSystem,
            @Value("${gdpr.vendorlist.filesystem-cache-dir}") String cacheDir,
            HttpClient httpClient,
            @Value("${gdpr.vendorlist.http-endpoint-template}") String endpointTemplate,
            @Value("${gdpr.vendorlist.http-default-timeout-ms}") int defaultTimeoutMs,
            @Value("${gdpr.host-vendor-id:#{null}}") Integer hostVendorId,
            BidderCatalog bidderCatalog) {

        return VendorListService.create(fileSystem, cacheDir, httpClient, endpointTemplate, defaultTimeoutMs,
                hostVendorId, bidderCatalog);
    }

    @Bean
    GdprService gdprService(
            @Autowired(required = false) GeoLocationService geoLocationService,
            VendorListService vendorListService,
            @Value("${gdpr.eea-countries}") String eeaCountriesAsString,
            @Value("${gdpr.default-value}") String defaultValue) {

        final List<String> eeaCountries = Arrays.asList(eeaCountriesAsString.trim().split(","));
        return new GdprService(geoLocationService, vendorListService, eeaCountries, defaultValue);
    }

    @Bean
    EventsService eventsService(@Value("${external-url}") String externalUrl) {
        return new EventsService(externalUrl);
    }

    @Bean
    BidderCatalog bidderCatalog(List<BidderDeps> bidderDeps) {
        return new BidderCatalog(bidderDeps);
    }

    @Bean
    HttpBidderRequester httpBidderRequester(HttpClient httpClient) {
        return new HttpBidderRequester(httpClient);
    }

    @Bean
    BidResponseCreator bidResponseCreator(
            BidderCatalog bidderCatalog,
            EventsService eventsService,
            CacheService cacheService) {

        return new BidResponseCreator(bidderCatalog, eventsService, cacheService.getEndpointHost(),
                cacheService.getEndpointPath(), cacheService.getCachedAssetURLTemplate());
    }

    @Bean
    ExchangeService exchangeService(
            BidderCatalog bidderCatalog,
            StoredResponseProcessor storedResponseProcessor,
            PrivacyEnforcementService privacyEnforcementService,
            HttpBidderRequester httpBidderRequester,
            ResponseBidValidator responseBidValidator,
            CurrencyConversionService currencyConversionService,
            CacheService cacheService,
            BidResponseCreator bidResponseCreator,
            BidResponsePostProcessor bidResponsePostProcessor,
            Metrics metrics,
            Clock clock,
            @Value("${auction.cache.expected-request-time-ms}") long expectedCacheTimeMs) {

        return new ExchangeService(bidderCatalog, storedResponseProcessor, privacyEnforcementService,
                httpBidderRequester, responseBidValidator, currencyConversionService, cacheService,
                bidResponseCreator, bidResponsePostProcessor, metrics, clock, expectedCacheTimeMs);
    }

    @Bean
    StoredRequestProcessor storedRequestProcessor(
            @Value("${auction.stored-requests-timeout-ms}") long defaultTimeoutMs,
            ApplicationSettings applicationSettings,
            Metrics metrics,
            TimeoutFactory timeoutFactory) {

        return new StoredRequestProcessor(applicationSettings, metrics, timeoutFactory, defaultTimeoutMs);
    }

    @Bean
    StoredResponseProcessor storedResponseProcessor(
            ApplicationSettings applicationSettings,
            BidderCatalog bidderCatalog) {

        return new StoredResponseProcessor(applicationSettings, bidderCatalog);
    }

    @Bean
    PrivacyEnforcementService privacyEnforcementService(
            GdprService gdprService,
            BidderCatalog bidderCatalog,
            Metrics metrics,
            @Value("${gdpr.geolocation.enabled}") boolean useGeoLocation) {
        return new PrivacyEnforcementService(gdprService, bidderCatalog, metrics, useGeoLocation);
    }

    @Bean
    HttpAdapterConnector httpAdapterConnector(HttpClient httpClient, Clock clock) {
        return new HttpAdapterConnector(httpClient, clock);
    }

    @Bean
    RequestValidator requestValidator(BidderCatalog bidderCatalog,
                                      BidderParamValidator bidderParamValidator) {
        return new RequestValidator(bidderCatalog, bidderParamValidator);
    }

    @Bean
    BidderParamValidator bidderParamValidator(BidderCatalog bidderCatalog) {
        return BidderParamValidator.create(bidderCatalog, "static/bidder-params");
    }

    @Bean
    ResponseBidValidator responseValidator() {
        return new ResponseBidValidator();
    }

    @Bean
    PublicSuffixList psl() {
        final PublicSuffixListFactory factory = new PublicSuffixListFactory();

        final Properties properties = factory.getDefaults();
        properties.setProperty(PublicSuffixListFactory.PROPERTY_LIST_FILE, "/effective_tld_names.dat");
        try {
            return factory.build(properties);
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Could not initialize public suffix list", e);
        }
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    TimeoutFactory timeoutFactory(Clock clock) {
        return new TimeoutFactory(clock);
    }

    @Bean
    BidResponsePostProcessor bidResponsePostProcessor() {
        return BidResponsePostProcessor.noOp();
    }

    @Bean
    AmpResponsePostProcessor ampResponsePostProcessor() {
        return AmpResponsePostProcessor.noOp();
    }

    @Bean
    @ConditionalOnProperty(prefix = "currency-converter", name = "enabled", havingValue = "true")
    CurrencyConversionService currencyConversionService(
            @Value("${currency-converter.url}") String currencyServerUrl,
            @Value("${currency-converter.default-timeout-ms}") long defaultTimeout,
            @Value("${currency-converter.refresh-period-ms}") long refreshPeriod,
            Vertx vertx,
            HttpClient httpClient) {

        return new CurrencyConversionService(currencyServerUrl, defaultTimeout, refreshPeriod, vertx, httpClient);
    }

    @Configuration
    @ConditionalOnProperty(prefix = "gdpr.geolocation", name = "enabled", havingValue = "true")
    static class GeoLocationConfiguration {

        @Bean
        @ConfigurationProperties(prefix = "gdpr.geolocation.maxmind.remote-file-syncer")
        RemoteFileSyncerProperties maxMindRemoteFileSyncerProperties() {
            return new RemoteFileSyncerProperties();
        }

        /**
         * Default geolocation service implementation.
         */

        @Bean
        @ConditionalOnProperty(prefix = "gdpr.geolocation.circuit-breaker", name = "enabled", havingValue = "false",
                matchIfMissing = true)
        GeoLocationService basicGeoLocationService(RemoteFileSyncerProperties fileSyncerProperties, Vertx vertx) {

            return createGeoLocationService(fileSyncerProperties, vertx);
        }

        @Bean
        @ConfigurationProperties(prefix = "gdpr.geolocation.circuit-breaker")
        @ConditionalOnProperty(prefix = "gdpr.geolocation.circuit-breaker", name = "enabled", havingValue = "true")
        CircuitBreakerProperties geolocationCircuitBreakerProperties() {
            return new CircuitBreakerProperties();
        }

        @Bean
        @ConditionalOnProperty(prefix = "gdpr.geolocation.circuit-breaker", name = "enabled", havingValue = "true")
        CircuitBreakerSecuredGeoLocationService circuitBreakerSecuredGeoLocationService(
                Vertx vertx,
                Metrics metrics,
                RemoteFileSyncerProperties fileSyncerProperties,
                @Qualifier("geolocationCircuitBreakerProperties") CircuitBreakerProperties circuitBreakerProperties,
                Clock clock) {

            return new CircuitBreakerSecuredGeoLocationService(vertx,
                    createGeoLocationService(fileSyncerProperties, vertx), metrics,
                    circuitBreakerProperties.getOpeningThreshold(), circuitBreakerProperties.getOpeningIntervalMs(),
                    circuitBreakerProperties.getClosingIntervalMs(), clock);
        }

        private GeoLocationService createGeoLocationService(RemoteFileSyncerProperties fileSyncerProperties,
                                                            Vertx vertx) {

            final HttpClientProperties httpClientProperties = fileSyncerProperties.getHttpClient();
            final HttpClientOptions httpClientOptions = new HttpClientOptions()
                    .setConnectTimeout(httpClientProperties.getConnectTimeoutMs())
                    .setMaxRedirects(httpClientProperties.getMaxRedirects());

            final RemoteFileSyncer remoteFileSyncer = RemoteFileSyncer.create(fileSyncerProperties.getDownloadUrl(),
                    fileSyncerProperties.getSaveFilepath(), fileSyncerProperties.getRetryCount(),
                    fileSyncerProperties.getRetryIntervalMs(), fileSyncerProperties.getTimeoutMs(),
                    vertx.createHttpClient(httpClientOptions), vertx);
            final MaxMindGeoLocationService maxMindGeoLocationService = new MaxMindGeoLocationService();

            remoteFileSyncer.syncForFilepath(maxMindGeoLocationService::setDatabaseReader);

            return maxMindGeoLocationService;
        }
    }

    @Configuration
    @ConditionalOnProperty("status-response")
    @ConditionalOnExpression("'${status-response}' != ''")
    static class HealthCheckerConfiguration {

        @Bean
        @ConditionalOnProperty(prefix = "health-check.database", name = "enabled", havingValue = "true")
        HealthChecker databaseChecker(
                Vertx vertx,
                JDBCClient jdbcClient,
                @Value("${health-check.database.refresh-period-ms}") long refreshPeriod) {

            return new DatabaseHealthChecker(vertx, jdbcClient, refreshPeriod);
        }

        @Bean
        HealthChecker applicationChecker(@Value("${status-response}") String statusResponse) {
            return new ApplicationChecker(statusResponse);
        }
    }
}
