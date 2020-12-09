package org.prebid.server.spring.config;

import com.iab.openrtb.request.BidRequest;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.JksOptions;
import org.prebid.server.auction.AmpRequestFactory;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.AuctionRequestFactory;
import org.prebid.server.auction.BidResponseCreator;
import org.prebid.server.auction.BidResponsePostProcessor;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.FpdResolver;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.InterstitialProcessor;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.OrtbTypesResolver;
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
import org.prebid.server.bidder.BidderErrorNotifier;
import org.prebid.server.bidder.BidderRequestCompletionTrackerFactory;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.events.EventsService;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.identity.IdGeneratorType;
import org.prebid.server.identity.NoneIdGenerator;
import org.prebid.server.identity.UUIDIdGenerator;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.log.LoggerControlKnob;
import org.prebid.server.metric.Metrics;
import org.prebid.server.optout.GoogleRecaptchaVerifier;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.spring.config.model.CircuitBreakerProperties;
import org.prebid.server.spring.config.model.ExternalConversionProperties;
import org.prebid.server.spring.config.model.HttpClientProperties;
import org.prebid.server.util.VersionInfo;
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
import java.util.concurrent.TimeUnit;
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
    IpAddressHelper ipAddressHelper(@Value("${ipv6.always-mask-right}") int ipv6AlwaysMaskBits,
                                    @Value("${ipv6.anon-left-mask-bits}") int ipv6AnonLeftMaskBits,
                                    @Value("${ipv6.private-networks}") String ipv6PrivateNetworksAsString) {

        final List<String> ipv6LocalNetworks = Arrays.asList(ipv6PrivateNetworksAsString.trim().split(","));

        return new IpAddressHelper(ipv6AlwaysMaskBits, ipv6AnonLeftMaskBits, ipv6LocalNetworks);
    }

    @Bean
    FpdResolver fpdResolver(JacksonMapper mapper) {
        return new FpdResolver(mapper);
    }

    @Bean
    OrtbTypesResolver ortbTypesResolver(JacksonMapper jacksonMapper) {
        return new OrtbTypesResolver(jacksonMapper);
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
            IpAddressHelper ipAddressHelper,
            ApplicationSettings applicationSettings,
            UidsCookieService uidsCookieService,
            TimeoutFactory timeoutFactory,
            JacksonMapper mapper) {

        return new PreBidRequestContextFactory(
                timeoutResolver,
                implicitParametersExtractor,
                ipAddressHelper,
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
            @Value("${auction.ad-server-currency}") String adServerCurrency,
            @Value("${auction.blacklisted-apps}") String blacklistedAppsString,
            @Value("${auction.blacklisted-accounts}") String blacklistedAccountsString,
            StoredRequestProcessor storedRequestProcessor,
            ImplicitParametersExtractor implicitParametersExtractor,
            IpAddressHelper ipAddressHelper,
            UidsCookieService uidsCookieService,
            BidderCatalog bidderCatalog,
            RequestValidator requestValidator,
            OrtbTypesResolver ortbTypesResolver,
            TimeoutResolver timeoutResolver,
            TimeoutFactory timeoutFactory,
            ApplicationSettings applicationSettings,
            PrivacyEnforcementService privacyEnforcementService,
            IdGenerator idGenerator,
            JacksonMapper mapper) {

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
                ipAddressHelper,
                uidsCookieService,
                bidderCatalog,
                requestValidator,
                new InterstitialProcessor(),
                ortbTypesResolver,
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                idGenerator,
                privacyEnforcementService,
                mapper);
    }

    @Bean
    IdGenerator idGenerator(@Value("${auction.id-generator-type}") IdGeneratorType idGeneratorType) {
        return idGeneratorType == IdGeneratorType.uuid
                ? new UUIDIdGenerator()
                : new NoneIdGenerator();
    }

    @Bean
    AmpRequestFactory ampRequestFactory(StoredRequestProcessor storedRequestProcessor,
                                        AuctionRequestFactory auctionRequestFactory,
                                        OrtbTypesResolver ortbTypesResolver,
                                        ImplicitParametersExtractor implicitParametersExtractor,
                                        FpdResolver fpdResolver,
                                        TimeoutResolver timeoutResolver,
                                        JacksonMapper mapper) {

        return new AmpRequestFactory(
                storedRequestProcessor,
                auctionRequestFactory,
                ortbTypesResolver,
                implicitParametersExtractor,
                fpdResolver,
                timeoutResolver,
                mapper);
    }

    @Bean
    VideoRequestFactory videoRequestFactory(
            @Value("${auction.max-request-size}") int maxRequestSize,
            @Value("${video.stored-request-required}") boolean enforceStoredRequest,
            VideoStoredRequestProcessor storedRequestProcessor,
            AuctionRequestFactory auctionRequestFactory,
            TimeoutResolver timeoutResolver,
            JacksonMapper mapper) {

        return new VideoRequestFactory(
                maxRequestSize,
                enforceStoredRequest,
                storedRequestProcessor,
                auctionRequestFactory,
                timeoutResolver,
                mapper);
    }

    @Bean
    VideoResponseFactory videoResponseFactory(JacksonMapper mapper) {
        return new VideoResponseFactory(mapper);
    }

    @Bean
    VideoStoredRequestProcessor videoStoredRequestProcessor(
            @Value("${video.stored-request-required}") boolean enforceStoredRequest,
            @Value("${auction.blacklisted-accounts}") String blacklistedAccountsString,
            @Value("${video.stored-requests-timeout-ms}") long defaultTimeoutMs,
            @Value("${auction.ad-server-currency:#{null}}") String adServerCurrency,
            BidRequest defaultVideoBidRequest,
            ApplicationSettings applicationSettings,
            Metrics metrics,
            TimeoutFactory timeoutFactory,
            TimeoutResolver timeoutResolver,
            JacksonMapper mapper) {

        final List<String> blacklistedAccounts = splitCommaSeparatedString(blacklistedAccountsString);

        return new VideoStoredRequestProcessor(
                enforceStoredRequest,
                blacklistedAccounts,
                defaultTimeoutMs,
                adServerCurrency,
                defaultVideoBidRequest,
                new VideoRequestValidator(),
                applicationSettings,
                metrics,
                timeoutFactory,
                timeoutResolver,
                mapper);
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
        return createBasicHttpClient(vertx, httpClientProperties);
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

        final HttpClient httpClient = createBasicHttpClient(vertx, httpClientProperties);

        return new CircuitBreakerSecuredHttpClient(vertx, httpClient, metrics,
                circuitBreakerProperties.getOpeningThreshold(), circuitBreakerProperties.getOpeningIntervalMs(),
                circuitBreakerProperties.getClosingIntervalMs(), clock);
    }

    private static BasicHttpClient createBasicHttpClient(Vertx vertx, HttpClientProperties httpClientProperties) {
        final HttpClientOptions options = new HttpClientOptions()
                .setMaxPoolSize(httpClientProperties.getMaxPoolSize())
                .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)
                .setIdleTimeout(httpClientProperties.getIdleTimeoutMs())
                .setPoolCleanerPeriod(httpClientProperties.getPoolCleanerPeriodMs())
                .setTryUseCompression(httpClientProperties.getUseCompression())
                .setConnectTimeout(httpClientProperties.getConnectTimeoutMs())
                // Vert.x's HttpClientRequest needs this value to be 2 for redirections to be followed once,
                // 3 for twice, and so on
                .setMaxRedirects(httpClientProperties.getMaxRedirects() + 1);

        if (httpClientProperties.getSsl()) {
            final JksOptions jksOptions = new JksOptions()
                    .setPath(httpClientProperties.getJksPath())
                    .setPassword(httpClientProperties.getJksPassword());

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
            @Autowired(required = false) BidderRequestCompletionTrackerFactory bidderRequestCompletionTrackerFactory,
            BidderErrorNotifier bidderErrorNotifier) {

        return new HttpBidderRequester(httpClient, bidderRequestCompletionTrackerFactory, bidderErrorNotifier);
    }

    @Bean
    BidderErrorNotifier bidderErrorNotifier(
            @Value("${auction.timeout-notification.timeout-ms}") int timeoutNotificationTimeoutMs,
            @Value("${auction.timeout-notification.log-result}") boolean logTimeoutNotificationResult,
            @Value("${auction.timeout-notification.log-failure-only}") boolean logTimeoutNotificationFailureOnly,
            @Value("${auction.timeout-notification.log-sampling-rate}") double logTimeoutNotificationSamplingRate,
            HttpClient httpClient,
            Metrics metrics) {

        return new BidderErrorNotifier(
                timeoutNotificationTimeoutMs,
                logTimeoutNotificationResult,
                logTimeoutNotificationFailureOnly,
                logTimeoutNotificationSamplingRate,
                httpClient,
                metrics);
    }

    @Bean
    BidResponseCreator bidResponseCreator(
            CacheService cacheService,
            BidderCatalog bidderCatalog,
            EventsService eventsService,
            StoredRequestProcessor storedRequestProcessor,
            @Value("${auction.generate-bid-id}") boolean generateBidId,
            @Value("${settings.targeting.truncate-attr-chars}") int truncateAttrChars,
            Clock clock,
            JacksonMapper mapper) {

        return new BidResponseCreator(
                cacheService,
                bidderCatalog,
                eventsService,
                storedRequestProcessor,
                generateBidId,
                truncateAttrChars,
                clock,
                mapper);
    }

    @Bean
    ExchangeService exchangeService(
            @Value("${auction.cache.expected-request-time-ms}") long expectedCacheTimeMs,
            BidderCatalog bidderCatalog,
            StoredResponseProcessor storedResponseProcessor,
            PrivacyEnforcementService privacyEnforcementService,
            FpdResolver fpdResolver,
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
                fpdResolver,
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
            BidderCatalog bidderCatalog,
            PrivacyExtractor privacyExtractor,
            TcfDefinerService tcfDefinerService,
            IpAddressHelper ipAddressHelper,
            Metrics metrics,
            @Value("${ccpa.enforce}") boolean ccpaEnforce,
            @Value("${lmt.enforce}") boolean lmtEnforce) {

        return new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, tcfDefinerService, ipAddressHelper, metrics, ccpaEnforce, lmtEnforce);
    }

    @Bean
    PrivacyExtractor privacyExtractor() {
        return new PrivacyExtractor();
    }

    @Bean
    HttpAdapterConnector httpAdapterConnector(HttpClient httpClient,
                                              PrivacyExtractor privacyExtractor,
                                              Clock clock,
                                              JacksonMapper mapper) {

        return new HttpAdapterConnector(httpClient, privacyExtractor, clock, mapper);
    }

    @Bean
    VersionInfo versionInfo(JacksonMapper jacksonMapper) {
        return VersionInfo.create("git-revision.json", jacksonMapper);
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
            @Value("${currency-converter.external-rates.default-timeout-ms}") long defaultTimeoutMs,
            @Value("${currency-converter.external-rates.refresh-period-ms}") long refreshPeriodMs,
            @Value("${currency-converter.external-rates.stale-after-ms}") long staleAfterMs,
            Vertx vertx,
            HttpClient httpClient,
            Metrics metrics,
            Clock clock,
            JacksonMapper mapper) {

        return new ExternalConversionProperties(
                currencyServerUrl,
                defaultTimeoutMs,
                refreshPeriodMs,
                staleAfterMs,
                vertx,
                httpClient,
                metrics,
                clock,
                mapper);
    }

    @Bean
    HttpInteractionLogger httpInteractionLogger() {
        return new HttpInteractionLogger();
    }

    @Bean
    LoggerControlKnob loggerControlKnob(Vertx vertx) {
        return new LoggerControlKnob(vertx);
    }

    private static List<String> splitCommaSeparatedString(String listString) {
        return Stream.of(listString.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }
}
