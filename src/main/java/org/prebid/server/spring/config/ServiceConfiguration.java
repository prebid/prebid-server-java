package org.prebid.server.spring.config;

import com.iab.openrtb.request.BidRequest;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.JksOptions;
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
import org.prebid.server.auction.VideoRequestFactory;
import org.prebid.server.auction.VideoResponseFactory;
import org.prebid.server.auction.VideoStoredRequestProcessor;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderRequestCompletionTrackerFactory;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.events.EventsService;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.manager.AdminManager;
import org.prebid.server.metric.Metrics;
import org.prebid.server.optout.GoogleRecaptchaVerifier;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.privacy.gdpr.GdprService;
import org.prebid.server.privacy.gdpr.Tcf2Service;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.tcf2stratgies.PurposeOneStrategy;
import org.prebid.server.privacy.gdpr.tcf2stratgies.PurposeStrategy;
import org.prebid.server.privacy.gdpr.tcf2stratgies.typeStrategies.BasicTypeStrategy;
import org.prebid.server.privacy.gdpr.tcf2stratgies.typeStrategies.NoTypeStrategy;
import org.prebid.server.privacy.gdpr.vendorlist.VendorListService;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.Purposes;
import org.prebid.server.settings.model.SpecialFeature;
import org.prebid.server.settings.model.SpecialFeatures;
import org.prebid.server.spring.config.model.CircuitBreakerProperties;
import org.prebid.server.spring.config.model.ExternalConversionProperties;
import org.prebid.server.spring.config.model.HttpClientProperties;
import org.prebid.server.validation.BidderParamValidator;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.VideoRequestValidator;
import org.prebid.server.vertx.http.BasicHttpClient;
import org.prebid.server.vertx.http.CircuitBreakerSecuredHttpClient;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
            EventsService eventsService,
            HttpClient httpClient,
            Metrics metrics,
            Clock clock,
            JacksonMapper mapper) {

        return new CacheService(
                CacheTtl.of(bannerCacheTtl, videoCacheTtl),
                httpClient,
                CacheService.getCacheEndpointUrl(scheme, host, path),
                CacheService.getCachedAssetUrlTemplate(scheme, host, path, query),
                eventsService,
                metrics,
                clock,
                mapper);
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
            TimeoutFactory timeoutFactory,
            JacksonMapper mapper) {

        return new PreBidRequestContextFactory(
                timeoutResolver,
                implicitParametersExtractor,
                applicationSettings,
                uidsCookieService,
                timeoutFactory,
                mapper);
    }

    @Bean
    AuctionRequestFactory auctionRequestFactory(
            @Value("${auction.max-request-size}") @Min(0) int maxRequestSize,
            @Value("${settings.enforce-valid-account}") boolean enforceValidAccount,
            @Value("${auction.cache.only-winning-bids}") boolean shouldCacheOnlyWinningBids,
            @Value("${auction.ad-server-currency:#{null}}") String adServerCurrency,
            @Value("${auction.blacklisted-apps}") String blacklistedAppsString,
            @Value("${auction.blacklisted-accounts}") String blacklistedAccountsString,
            StoredRequestProcessor storedRequestProcessor,
            ImplicitParametersExtractor implicitParametersExtractor,
            UidsCookieService uidsCookieService,
            BidderCatalog bidderCatalog,
            RequestValidator requestValidator,
            TimeoutResolver timeoutResolver,
            TimeoutFactory timeoutFactory,
            ApplicationSettings applicationSettings,
            JacksonMapper mapper,
            AdminManager adminManager) {

        final List<String> blacklistedApps = splitCommaSeparatedString(blacklistedAppsString);
        final List<String> blacklistedAccounts = splitCommaSeparatedString(blacklistedAccountsString);

        return new AuctionRequestFactory(
                maxRequestSize,
                enforceValidAccount,
                shouldCacheOnlyWinningBids,
                adServerCurrency,
                blacklistedApps,
                blacklistedAccounts,
                storedRequestProcessor,
                implicitParametersExtractor,
                uidsCookieService,
                bidderCatalog,
                requestValidator,
                new InterstitialProcessor(mapper),
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                mapper);
    }

    private static List<String> splitCommaSeparatedString(String listString) {
        return Stream.of(listString.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    @Bean
    AmpRequestFactory ampRequestFactory(StoredRequestProcessor storedRequestProcessor,
                                        AuctionRequestFactory auctionRequestFactory,
                                        TimeoutResolver timeoutResolver,
                                        JacksonMapper mapper) {

        return new AmpRequestFactory(storedRequestProcessor, auctionRequestFactory, timeoutResolver, mapper);
    }

    @Bean
    VideoRequestFactory videoRequestFactory(
            @Value("${auction.max-request-size}") int maxRequestSize,
            @Value("${auction.video.stored-required:#{false}}") boolean enforceStoredRequest,
            VideoStoredRequestProcessor storedRequestProcessor,
            AuctionRequestFactory auctionRequestFactory,
            TimeoutResolver timeoutResolver, JacksonMapper mapper) {

        return new VideoRequestFactory(maxRequestSize, enforceStoredRequest, storedRequestProcessor,
                auctionRequestFactory, timeoutResolver, mapper);
    }

    @Bean
    VideoResponseFactory videoResponseFactory(JacksonMapper mapper) {
        return new VideoResponseFactory(mapper);
    }

    @Bean
    VideoStoredRequestProcessor videoStoredRequestProcessor(
            ApplicationSettings applicationSettings,
            @Value("${auction.video.stored-required:#{false}}") boolean enforceStoredRequest,
            @Value("${auction.blacklisted-accounts}") String blacklistedAccountsString,
            BidRequest defaultVideoBidRequest,
            Metrics metrics,
            TimeoutFactory timeoutFactory,
            TimeoutResolver timeoutResolver,
            @Value("${video.stored-requests-timeout-ms}") long defaultTimeoutMs,
            @Value("${auction.ad-server-currency:#{null}}") String adServerCurrency,
            JacksonMapper mapper) {

        final List<String> blacklistedAccounts = splitCommaSeparatedString(blacklistedAccountsString);

        return new VideoStoredRequestProcessor(applicationSettings, new VideoRequestValidator(), enforceStoredRequest,
                blacklistedAccounts, defaultVideoBidRequest, metrics, timeoutFactory, timeoutResolver, defaultTimeoutMs,
                adServerCurrency, mapper);
    }

    @Bean
    BidRequest defaultVideoBidRequest() {
        return BidRequest.builder().build();
    }

    @Bean
    GoogleRecaptchaVerifier googleRecaptchaVerifier(
            @Value("${recaptcha-url}") String recaptchaUrl,
            @Value("${recaptcha-secret}") String recaptchaSecret,
            HttpClient httpClient,
            JacksonMapper mapper) {

        return new GoogleRecaptchaVerifier(recaptchaUrl, recaptchaSecret, httpClient, mapper);
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
                httpClientProperties.getMaxRedirects(), httpClientProperties.getSsl(),
                httpClientProperties.getJksPath(), httpClientProperties.getJksPassword());
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
                httpClientProperties.getMaxRedirects(), httpClientProperties.getSsl(),
                httpClientProperties.getJksPath(), httpClientProperties.getJksPassword());
        return new CircuitBreakerSecuredHttpClient(vertx, httpClient, metrics,
                circuitBreakerProperties.getOpeningThreshold(), circuitBreakerProperties.getOpeningIntervalMs(),
                circuitBreakerProperties.getClosingIntervalMs(), clock);
    }

    private static BasicHttpClient createBasicHttpClient(Vertx vertx, int maxPoolSize, int connectTimeoutMs,
                                                         boolean useCompression, int maxRedirects, boolean ssl,
                                                         String jksPath, String jksPassword) {

        final HttpClientOptions options = new HttpClientOptions()
                .setMaxPoolSize(maxPoolSize)
                .setTryUseCompression(useCompression)
                .setConnectTimeout(connectTimeoutMs)
                // Vert.x's HttpClientRequest needs this value to be 2 for redirections to be followed once,
                // 3 for twice, and so on
                .setMaxRedirects(maxRedirects + 1);

        if (ssl) {
            final JksOptions jksOptions = new JksOptions()
                    .setPath(jksPath)
                    .setPassword(jksPassword);

            options
                    .setSsl(true)
                    .setKeyStoreOptions(jksOptions);
        }
        return new BasicHttpClient(vertx, vertx.createHttpClient(options));
    }

    @Bean
    UidsCookieService uidsCookieService(
            @Value("${host-cookie.optout-cookie.name:#{null}}") String optOutCookieName,
            @Value("${host-cookie.optout-cookie.value:#{null}}") String optOutCookieValue,
            @Value("${host-cookie.family:#{null}}") String hostCookieFamily,
            @Value("${host-cookie.cookie-name:#{null}}") String hostCookieName,
            @Value("${host-cookie.domain:#{null}}") String hostCookieDomain,
            @Value("${host-cookie.ttl-days}") Integer ttlDays,
            @Value("${host-cookie.max-cookie-size-bytes}") Integer maxCookieSizeBytes,
            JacksonMapper mapper) {

        return new UidsCookieService(
                optOutCookieName,
                optOutCookieValue,
                hostCookieFamily,
                hostCookieName,
                hostCookieDomain,
                ttlDays,
                maxCookieSizeBytes,
                mapper);
    }

    @Bean
    VendorListService vendorListService(
            @Value("${gdpr.vendorlist.filesystem-cache-dir}") String cacheDir,
            @Value("${gdpr.vendorlist.http-endpoint-template}") String endpointTemplate,
            @Value("${gdpr.vendorlist.http-default-timeout-ms}") int defaultTimeoutMs,
            @Value("${gdpr.host-vendor-id:#{null}}") Integer hostVendorId,
            BidderCatalog bidderCatalog,
            FileSystem fileSystem,
            HttpClient httpClient,
            JacksonMapper mapper) {

        return VendorListService.create(
                cacheDir,
                endpointTemplate,
                defaultTimeoutMs,
                hostVendorId,
                bidderCatalog,
                fileSystem,
                httpClient,
                mapper);
    }

    @Bean
    GdprService gdprService(
            @Value("${gdpr.eea-countries}") String eeaCountriesAsString,
            @Value("${gdpr.default-value}") String defaultValue,
            @Autowired(required = false) GeoLocationService geoLocationService,
            BidderCatalog bidderCatalog,
            Metrics metrics,
            VendorListService vendorListService) {

        final List<String> eeaCountries = Arrays.asList(eeaCountriesAsString.trim().split(","));
        return new GdprService(eeaCountries, defaultValue, geoLocationService, metrics, bidderCatalog,
                vendorListService);
    }

    @Bean
    Tcf2Service tcf2Service(
            GdprConfig gdprConfig,
            BidderCatalog bidderCatalog,
            List<PurposeStrategy> purposeStrategies) {

        return new Tcf2Service(gdprConfig, bidderCatalog, purposeStrategies);
    }

    @Bean
    PurposeOneStrategy purposeOneStrategy(BasicTypeStrategy basicTypeStrategy, NoTypeStrategy noTypeStrategy) {
        return new PurposeOneStrategy(basicTypeStrategy, noTypeStrategy);
    }

    @Bean
    BasicTypeStrategy basicTypeStrategy() {
        return new BasicTypeStrategy();
    }

    @Bean
    NoTypeStrategy noTypeStrategy() {
        return new NoTypeStrategy();
    }

    @Bean
    TcfDefinerService tcfDefinerService(
            GdprConfig gdprConfig,
            @Value("${gdpr.eea-countries}") String eeaCountriesAsString,
            GdprService gdprService,
            Tcf2Service tcf2Service,
            @Autowired(required = false) GeoLocationService geoLocationService,
            Metrics metrics) {

        final List<String> eeaCountries = Arrays.asList(eeaCountriesAsString.trim().split(","));
        return new TcfDefinerService(gdprConfig, eeaCountries, gdprService, tcf2Service, geoLocationService, metrics);
    }

    @Bean
    @ConfigurationProperties(prefix = "gdpr")
    GdprConfig gdprConfig() {
        return new GdprConfig();
    }

    @Bean
    @ConfigurationProperties(prefix = "gdpr.purposes")
    Purposes purposes() {
        return new Purposes();
    }

    @Bean
    Purpose purpose() {
        return new Purpose();
    }

    @Bean
    @ConfigurationProperties(prefix = "gdpr.special-features")
    SpecialFeatures specialFeatures() {
        return new SpecialFeatures();
    }

    @Bean
    SpecialFeature specialFeature() {
        return new SpecialFeature();
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
    HttpBidderRequester httpBidderRequester(
            HttpClient httpClient,
            @Autowired(required = false) BidderRequestCompletionTrackerFactory bidderRequestCompletionTrackerFactory) {

        return new HttpBidderRequester(httpClient, bidderRequestCompletionTrackerFactory);
    }

    @Bean
    BidResponseCreator bidResponseCreator(
            CacheService cacheService,
            BidderCatalog bidderCatalog,
            EventsService eventsService,
            StoredRequestProcessor storedRequestProcessor,
            JacksonMapper mapper) {

        return new BidResponseCreator(cacheService, bidderCatalog, eventsService, storedRequestProcessor, mapper);
    }

    @Bean
    ExchangeService exchangeService(
            @Value("${auction.cache.expected-request-time-ms}") long expectedCacheTimeMs,
            BidderCatalog bidderCatalog,
            StoredResponseProcessor storedResponseProcessor,
            PrivacyEnforcementService privacyEnforcementService,
            HttpBidderRequester httpBidderRequester,
            ResponseBidValidator responseBidValidator,
            CurrencyConversionService currencyConversionService,
            BidResponseCreator bidResponseCreator,
            BidResponsePostProcessor bidResponsePostProcessor,
            Metrics metrics,
            Clock clock,
            JacksonMapper mapper) {

        return new ExchangeService(
                expectedCacheTimeMs,
                bidderCatalog,
                storedResponseProcessor,
                privacyEnforcementService,
                httpBidderRequester,
                responseBidValidator,
                currencyConversionService,
                bidResponseCreator,
                bidResponsePostProcessor,
                metrics,
                clock,
                mapper);
    }

    @Bean
    StoredRequestProcessor storedRequestProcessor(
            @Value("${auction.stored-requests-timeout-ms}") long defaultTimeoutMs,
            ApplicationSettings applicationSettings,
            Metrics metrics,
            TimeoutFactory timeoutFactory,
            JacksonMapper mapper) {

        return new StoredRequestProcessor(defaultTimeoutMs, applicationSettings, metrics, timeoutFactory, mapper);
    }

    @Bean
    StoredResponseProcessor storedResponseProcessor(ApplicationSettings applicationSettings,
                                                    BidderCatalog bidderCatalog,
                                                    JacksonMapper mapper) {

        return new StoredResponseProcessor(applicationSettings, bidderCatalog, mapper);
    }

    @Bean
    PrivacyEnforcementService privacyEnforcementService(
            GdprService gdprService,
            BidderCatalog bidderCatalog,
            Metrics metrics,
            @Value("${geolocation.enabled}") boolean useGeoLocation,
            @Value("${ccpa.enforce}") boolean ccpaEnforce,
            JacksonMapper mapper) {
        return new PrivacyEnforcementService(gdprService, bidderCatalog, metrics, mapper, useGeoLocation, ccpaEnforce);
    }

    @Bean
    PrivacyExtractor privacyExtractor(JacksonMapper mapper) {
        return new PrivacyExtractor(mapper);
    }

    @Bean
    HttpAdapterConnector httpAdapterConnector(HttpClient httpClient,
                                              PrivacyExtractor privacyExtractor,
                                              Clock clock,
                                              JacksonMapper mapper) {

        return new HttpAdapterConnector(httpClient, privacyExtractor, clock, mapper);
    }

    @Bean
    RequestValidator requestValidator(BidderCatalog bidderCatalog,
                                      BidderParamValidator bidderParamValidator,
                                      JacksonMapper mapper) {

        return new RequestValidator(bidderCatalog, bidderParamValidator, mapper);
    }

    @Bean
    BidderParamValidator bidderParamValidator(BidderCatalog bidderCatalog, JacksonMapper mapper) {
        return BidderParamValidator.create(bidderCatalog, "static/bidder-params", mapper);
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
        return Clock.systemUTC();
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
            @Autowired(required = false) ExternalConversionProperties externalConversionProperties) {
        return new CurrencyConversionService(externalConversionProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "currency-converter.external-rates", name = "enabled", havingValue = "true")
    ExternalConversionProperties externalConversionProperties(
            @Value("${currency-converter.external-rates.url}") String currencyServerUrl,
            @Value("${currency-converter.external-rates.default-timeout-ms}") long defaultTimeout,
            @Value("${currency-converter.external-rates.refresh-period-ms}") long refreshPeriod,
            Vertx vertx,
            HttpClient httpClient,
            JacksonMapper mapper) {

        return new ExternalConversionProperties(currencyServerUrl, defaultTimeout, refreshPeriod, vertx, httpClient,
                mapper);
    }

    @Bean
    AdminManager adminManager() {
        return new AdminManager();
    }
}
