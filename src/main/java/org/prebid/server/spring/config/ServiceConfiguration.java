package org.prebid.server.spring.config;

import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClientOptions;
import org.prebid.server.auction.AmpRequestFactory;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.AuctionRequestFactory;
import org.prebid.server.auction.BidResponsePostProcessor;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.PreBidRequestContextFactory;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.vendorlist.VendorListService;
import org.prebid.server.geolocation.CircuitBreakerSecuredGeoLocationService;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.metric.Metrics;
import org.prebid.server.optout.GoogleRecaptchaVerifier;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.validation.BidderParamValidator;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.vertx.ContextRunner;
import org.prebid.server.vertx.http.BasicHttpClient;
import org.prebid.server.vertx.http.CircuitBreakerSecuredHttpClient;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

@Configuration
public class ServiceConfiguration {

    @Bean
    CacheService cacheService(
            @Value("${cache.scheme}") String scheme,
            @Value("${cache.host}") String host,
            @Value("${cache.query}") String query,
            @Value("${cache.hostPath:}") String hostPath,
            HttpClient httpClient) {

        return new CacheService(
                httpClient,
                CacheService.getCacheEndpointUrl(scheme, host),
                CacheService.getCachedAssetUrlTemplate(scheme, host, query),
                hostPath);
    }

    @Bean
    ImplicitParametersExtractor implicitParametersExtractor(PublicSuffixList psl) {
        return new ImplicitParametersExtractor(psl);
    }

    @Bean
    PreBidRequestContextFactory preBidRequestContextFactory(
            @Value("${default-timeout-ms}") long defaultTimeoutMs,
            @Value("${max-timeout-ms}") long maxTimeoutMs,
            ImplicitParametersExtractor implicitParametersExtractor,
            ApplicationSettings applicationSettings,
            UidsCookieService uidsCookieService,
            TimeoutFactory timeoutFactory) {

        return new PreBidRequestContextFactory(defaultTimeoutMs, maxTimeoutMs, implicitParametersExtractor,
                applicationSettings, uidsCookieService, timeoutFactory);
    }

    @Bean
    AuctionRequestFactory auctionRequestFactory(
            @Value("${auction.max-request-size}") @Min(0) int maxRequestSize,
            @Value("${auction.ad-server-currency:#{null}}") String adServerCurrency,
            StoredRequestProcessor storedRequestProcessor,
            ImplicitParametersExtractor implicitParametersExtractor,
            UidsCookieService uidsCookieService,
            RequestValidator requestValidator) {

        return new AuctionRequestFactory(maxRequestSize, adServerCurrency, storedRequestProcessor,
                implicitParametersExtractor, uidsCookieService, requestValidator);
    }

    @Bean
    AmpRequestFactory ampRequestFactory(@Value("${amp.timeout-adjustment-ms}") long timeoutAdjustmentMs,
                                        StoredRequestProcessor storedRequestProcessor,
                                        AuctionRequestFactory auctionRequestFactory) {
        return new AmpRequestFactory(timeoutAdjustmentMs, storedRequestProcessor, auctionRequestFactory);
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
        return new BasicHttpClient(vertx.createHttpClient(options));
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
         * Geo location service is not implemented by default.
         * It can be provided by vendor (host company) itself.
         */
        private GeoLocationService createGeoLocationService() {
            throw new RuntimeException("Geo location service is not implemented");
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
    ExchangeService exchangeService(
            @Value("${auction.expected-cache-time-ms}") long expectedCacheTimeMs,
            @Value("${gdpr.geolocation.enabled}") boolean useGeoLocation,
            BidderCatalog bidderCatalog,
            ResponseBidValidator responseBidValidator,
            CacheService cacheService,
            CurrencyConversionService currencyConversionService,
            GdprService gdprService,
            BidResponsePostProcessor bidResponsePostProcessor,
            Metrics metrics,
            Clock clock) {

        return new ExchangeService(bidderCatalog, responseBidValidator, cacheService, bidResponsePostProcessor,
                currencyConversionService, gdprService, metrics, clock, expectedCacheTimeMs, useGeoLocation);
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
    CurrencyConversionService currencyConversionRates(
            @Value("${auction.currency-rates-refresh-period-ms}") long refreshPeriod,
            @Value("${auction.currency-rates-url}") String currencyServerUrl,
            Vertx vertx,
            HttpClient httpClient,
            ContextRunner contextRunner) {

        final CurrencyConversionService service = new CurrencyConversionService(currencyServerUrl, refreshPeriod,
                vertx, httpClient);

        contextRunner.runOnServiceContext(future -> {
            service.initialize();
            future.complete();
        });

        return service;
    }
}
