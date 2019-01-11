package org.prebid.server.spring.config;

import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClientOptions;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.auction.AmpRequestFactory;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.AuctionRequestFactory;
import org.prebid.server.auction.BidResponsePostProcessor;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.PreBidRequestContextFactory;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.account.AccountCacheService;
import org.prebid.server.cache.account.SimpleAccountCacheService;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.vendorlist.VendorListService;
import org.prebid.server.geolocation.CircuitBreakerSecuredGeoLocationService;
import org.prebid.server.geolocation.GeoLite;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.metric.Metrics;
import org.prebid.server.optout.GoogleRecaptchaVerifier;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.validation.BidderParamValidator;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.vertx.http.BasicHttpClient;
import org.prebid.server.vertx.http.CircuitBreakerSecuredHttpClient;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.validation.constraints.Min;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
            AccountCacheService accountCacheService,
            HttpClient httpClient) {

        return new CacheService(
                accountCacheService,
                CacheTtl.of(bannerCacheTtl, videoCacheTtl),
                httpClient,
                CacheService.getCacheEndpointUrl(scheme, host, path),
                CacheService.getCachedAssetUrlTemplate(scheme, host, path, query));
    }

    @Bean
    AccountCacheService simpleAccountCacheService(AccountCacheProperties accountCacheProperties) {
        return new SimpleAccountCacheService(accountCacheProperties.getAccountToCacheTtl());
    }

    @Component
    @ConfigurationProperties(prefix = "cache")
    @Data
    @NoArgsConstructor
    private static class AccountCacheProperties {

        private Map<String, Map<String, Integer>> account;

        private final Map<String, CacheTtl> accountToCacheTtl = new HashMap<>();

        @PostConstruct
        private void init() {
            final Map<String, Map<String, Integer>> account = getAccount();
            if (account != null) {
                for (Map.Entry<String, Map<String, Integer>> entry : account.entrySet()) {
                    accountToCacheTtl.put(entry.getKey(), CacheTtl.of(
                            entry.getValue().get("banner-ttl-seconds"),
                            entry.getValue().get("video-ttl-seconds")));
                }
            }
        }
    }

    @Bean
    ImplicitParametersExtractor implicitParametersExtractor(PublicSuffixList psl) {
        return new ImplicitParametersExtractor(psl);
    }

    @Bean
    PreBidRequestContextFactory preBidRequestContextFactory(
            @Value("${default-timeout-ms}") long defaultTimeout,
            @Value("${max-timeout-ms}") long maxTimeout,
            @Value("${timeout-adjustment-ms}") long timeoutAdjustment,
            ImplicitParametersExtractor implicitParametersExtractor,
            ApplicationSettings applicationSettings,
            UidsCookieService uidsCookieService,
            TimeoutFactory timeoutFactory) {

        return new PreBidRequestContextFactory(defaultTimeout, maxTimeout, timeoutAdjustment,
                implicitParametersExtractor, applicationSettings, uidsCookieService, timeoutFactory);
    }

    @Bean
    AuctionRequestFactory auctionRequestFactory(
            @Value("${auction.default-timeout-ms}") long defaultTimeout,
            @Value("${auction.max-timeout-ms}") long maxTimeout,
            @Value("${auction.timeout-adjustment-ms}") long timeoutAdjustment,
            @Value("${auction.max-request-size}") @Min(0) int maxRequestSize,
            @Value("${auction.ad-server-currency:#{null}}") String adServerCurrency,
            StoredRequestProcessor storedRequestProcessor,
            ImplicitParametersExtractor implicitParametersExtractor,
            UidsCookieService uidsCookieService,
            BidderCatalog bidderCatalog,
            RequestValidator requestValidator) {

        return new AuctionRequestFactory(defaultTimeout, maxTimeout, timeoutAdjustment, maxRequestSize,
                adServerCurrency, storedRequestProcessor, implicitParametersExtractor, uidsCookieService, bidderCatalog,
                requestValidator);
    }

    @Bean
    AmpRequestFactory ampRequestFactory(
            @Value("${amp.default-timeout-ms}") long defaultTimeout,
            @Value("${amp.max-timeout-ms}") long maxTimeout,
            @Value("${amp.timeout-adjustment-ms}") long timeoutAdjustment,
            StoredRequestProcessor storedRequestProcessor,
            AuctionRequestFactory auctionRequestFactory) {

        return new AmpRequestFactory(defaultTimeout, maxTimeout, timeoutAdjustment, storedRequestProcessor,
                auctionRequestFactory);
    }

    @Bean
    GoogleRecaptchaVerifier googleRecaptchaVerifier(
            @Value("${recaptcha-url}") String recaptchaUrl,
            @Value("${recaptcha-secret}") String recaptchaSecret,
            HttpClient httpClient) {

        return new GoogleRecaptchaVerifier(httpClient, recaptchaUrl, recaptchaSecret);
    }

    @Bean
    @Scope(scopeName = VertxContextScope.NAME, proxyMode = ScopedProxyMode.INTERFACES)
    @ConditionalOnProperty(prefix = "http-client.circuit-breaker", name = "enabled", havingValue = "false",
            matchIfMissing = true)
    BasicHttpClient basicHttpClient(
            Vertx vertx,
            @Value("${http-client.max-pool-size}") int maxPoolSize,
            @Value("${http-client.connect-timeout-ms}") int connectTimeoutMs) {

        return createBasicHttpClient(vertx, maxPoolSize, connectTimeoutMs);
    }

    @Bean
    @Scope(scopeName = VertxContextScope.NAME, proxyMode = ScopedProxyMode.INTERFACES)
    @ConditionalOnProperty(prefix = "http-client.circuit-breaker", name = "enabled", havingValue = "true")
    CircuitBreakerSecuredHttpClient circuitBreakerSecuredHttpClient(
            Vertx vertx,
            Metrics metrics,
            @Value("${http-client.max-pool-size}") int maxPoolSize,
            @Value("${http-client.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${http-client.circuit-breaker.opening-threshold}") int openingThreshold,
            @Value("${http-client.circuit-breaker.opening-interval-ms}") long openingIntervalMs,
            @Value("${http-client.circuit-breaker.closing-interval-ms}") long closingIntervalMs) {

        final HttpClient httpClient = createBasicHttpClient(vertx, maxPoolSize, connectTimeoutMs);
        return new CircuitBreakerSecuredHttpClient(vertx, httpClient, metrics, openingThreshold, openingIntervalMs,
                closingIntervalMs);
    }

    private static BasicHttpClient createBasicHttpClient(Vertx vertx, int maxPoolSize, int connectTimeoutMs) {
        final HttpClientOptions options = new HttpClientOptions()
                .setMaxPoolSize(maxPoolSize)
                .setConnectTimeout(connectTimeoutMs);
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

    @Configuration
    @ConditionalOnProperty(prefix = "gdpr.geolocation", name = "enabled", havingValue = "true")
    static class GeoLocationConfiguration {

        @Bean
        @ConditionalOnProperty(prefix = "gdpr.geolocation.circuit-breaker", name = "enabled", havingValue = "false",
                matchIfMissing = true)
        GeoLocationService basicGeoLocationService() {

            return createGeoLocationService();
        }

        @Bean
        @ConditionalOnProperty(prefix = "gdpr.geolocation.circuit-breaker", name = "enabled", havingValue = "true")
        CircuitBreakerSecuredGeoLocationService circuitBreakerSecuredGeoLocationService(
                Vertx vertx,
                Metrics metrics,
                @Value("${gdpr.geolocation.circuit-breaker.opening-threshold}") int openingThreshold,
                @Value("${gdpr.geolocation.circuit-breaker.opening-interval-ms}") long openingIntervalMs,
                @Value("${gdpr.geolocation.circuit-breaker.closing-interval-ms}") long closingIntervalMs) {

            return new CircuitBreakerSecuredGeoLocationService(vertx, createGeoLocationService(), metrics,
                    openingThreshold, openingIntervalMs, closingIntervalMs);
        }

        /**
         * Default geolocation service implementation.
         */
        private GeoLocationService createGeoLocationService() {
            return GeoLite.create("GeoLite2-Country.tar.gz");
        }
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
    BidderCatalog bidderCatalog(List<BidderDeps> bidderDeps) {
        return new BidderCatalog(bidderDeps);
    }

    @Bean
    HttpBidderRequester httpBidderRequester(HttpClient httpClient) {
        return new HttpBidderRequester(httpClient);
    }

    @Bean
    ExchangeService exchangeService(
            BidderCatalog bidderCatalog,
            HttpBidderRequester httpBidderRequester,
            ResponseBidValidator responseBidValidator,
            CacheService cacheService,
            CurrencyConversionService currencyConversionService,
            GdprService gdprService,
            BidResponsePostProcessor bidResponsePostProcessor,
            Metrics metrics,
            Clock clock,
            @Value("${gdpr.geolocation.enabled}") boolean useGeoLocation,
            @Value("${auction.cache.expected-request-time-ms}") long expectedCacheTimeMs) {

        return new ExchangeService(bidderCatalog, httpBidderRequester, responseBidValidator, cacheService,
                bidResponsePostProcessor, currencyConversionService, gdprService, metrics, clock, useGeoLocation,
                expectedCacheTimeMs);
    }

    @Bean
    StoredRequestProcessor storedRequestProcessor(
            @Value("${auction.stored-requests-timeout-ms}") long defaultTimeoutMs,
            ApplicationSettings applicationSettings,
            TimeoutFactory timeoutFactory) {

        return new StoredRequestProcessor(applicationSettings, timeoutFactory, defaultTimeoutMs);
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
    CurrencyConversionService currencyConversionService(
            @Value("${auction.currency-rates-refresh-period-ms}") long refreshPeriod,
            @Value("${auction.currency-rates-url}") String currencyServerUrl,
            Vertx vertx,
            HttpClient httpClient) {

        return new CurrencyConversionService(currencyServerUrl, refreshPeriod, vertx, httpClient);
    }
}
