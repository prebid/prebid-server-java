package org.prebid.server.spring.config;

import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.JksOptions;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.BidResponseCreator;
import org.prebid.server.auction.BidResponsePostProcessor;
import org.prebid.server.auction.DebugResolver;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.FpdResolver;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.InterstitialProcessor;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.OrtbTypesResolver;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.SchainResolver;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.StoredResponseProcessor;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.VideoResponseFactory;
import org.prebid.server.auction.VideoStoredRequestProcessor;
import org.prebid.server.auction.WinningBidComparatorFactory;
import org.prebid.server.auction.categorymapping.BasicCategoryMappingService;
import org.prebid.server.auction.categorymapping.CategoryMappingService;
import org.prebid.server.auction.categorymapping.NoOpCategoryMappingService;
import org.prebid.server.auction.requestfactory.AmpRequestFactory;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
import org.prebid.server.auction.requestfactory.Ortb2ImplicitParametersResolver;
import org.prebid.server.auction.requestfactory.Ortb2RequestFactory;
import org.prebid.server.auction.requestfactory.VideoRequestFactory;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderErrorNotifier;
import org.prebid.server.bidder.BidderRequestCompletionTrackerFactory;
import org.prebid.server.bidder.HttpBidderRequestEnricher;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.deals.DealsProcessor;
import org.prebid.server.deals.events.ApplicationEventService;
import org.prebid.server.events.EventsService;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.identity.NoneIdGenerator;
import org.prebid.server.identity.UUIDIdGenerator;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.log.CriteriaManager;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.log.LoggerControlKnob;
import org.prebid.server.metric.Metrics;
import org.prebid.server.optout.GoogleRecaptchaVerifier;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.BidValidationEnforcement;
import org.prebid.server.spring.config.model.ExternalConversionProperties;
import org.prebid.server.spring.config.model.HttpClientCircuitBreakerProperties;
import org.prebid.server.spring.config.model.HttpClientProperties;
import org.prebid.server.util.VersionInfo;
import org.prebid.server.validation.BidderParamValidator;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.VideoRequestValidator;
import org.prebid.server.vast.VastModifier;
import org.prebid.server.version.PrebidVersionProvider;
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
            @Value("${auction.cache.expected-request-time-ms}") long expectedCacheTimeMs,
            VastModifier vastModifier,
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
                expectedCacheTimeMs,
                vastModifier,
                eventsService,
                metrics,
                clock,
                new UUIDIdGenerator(),
                mapper);
    }

    @Bean
    VastModifier vastModifier(BidderCatalog bidderCatalog, EventsService eventsService, Metrics metrics) {
        return new VastModifier(bidderCatalog, eventsService, metrics);
    }

    @Bean
    @ConditionalOnProperty(prefix = "auction", name = "category-mapping-enabled", havingValue = "true")
    CategoryMappingService basicCategoryMappingService(ApplicationSettings applicationSettings,
                                                       JacksonMapper jacksonMapper) {

        return new BasicCategoryMappingService(applicationSettings, jacksonMapper);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "auction",
            name = "category-mapping-enabled",
            matchIfMissing = true,
            havingValue = "false")
    CategoryMappingService noOpCategoryMappingService() {
        return new NoOpCategoryMappingService();
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
    FpdResolver fpdResolver(JacksonMapper mapper, JsonMerger jsonMerger) {
        return new FpdResolver(mapper, jsonMerger);
    }

    @Bean
    OrtbTypesResolver ortbTypesResolver(JacksonMapper jacksonMapper, JsonMerger jsonMerger) {
        return new OrtbTypesResolver(jacksonMapper, jsonMerger);
    }

    @Bean
    SchainResolver schainResolver(
            @Value("${auction.host-schain-node}") String globalSchainNode,
            JacksonMapper mapper) {

        return SchainResolver.create(globalSchainNode, mapper);
    }

    @Bean
    TimeoutResolver auctionTimeoutResolver(
            @Value("${auction.default-timeout-ms}") long defaultTimeout,
            @Value("${auction.max-timeout-ms}") long maxTimeout,
            @Value("${auction.timeout-adjustment-ms}") long timeoutAdjustment) {

        return new TimeoutResolver(defaultTimeout, maxTimeout, timeoutAdjustment);
    }

    @Bean
    DebugResolver debugResolver(@Value("${debug.override-token:#{null}}") String debugOverrideToken,
                                BidderCatalog bidderCatalog) {
        return new DebugResolver(bidderCatalog, debugOverrideToken);
    }

    @Bean
    Ortb2ImplicitParametersResolver ortb2ImplicitParametersResolver(
            @Value("${auction.cache.only-winning-bids}") boolean shouldCacheOnlyWinningBids,
            @Value("${auction.ad-server-currency}") String adServerCurrency,
            @Value("${auction.blacklisted-apps}") String blacklistedAppsString,
            ImplicitParametersExtractor implicitParametersExtractor,
            IpAddressHelper ipAddressHelper,
            IdGenerator sourceIdGenerator,
            JsonMerger jsonMerger,
            JacksonMapper mapper) {

        final List<String> blacklistedApps = splitToList(blacklistedAppsString);

        return new Ortb2ImplicitParametersResolver(
                shouldCacheOnlyWinningBids,
                adServerCurrency,
                blacklistedApps,
                implicitParametersExtractor,
                ipAddressHelper,
                sourceIdGenerator,
                jsonMerger,
                mapper);
    }

    @Bean
    Ortb2RequestFactory openRtb2RequestFactory(
            @Value("${settings.enforce-valid-account}") boolean enforceValidAccount,
            @Value("${auction.blacklisted-accounts}") String blacklistedAccountsString,
            UidsCookieService uidsCookieService,
            RequestValidator requestValidator,
            TimeoutResolver auctionTimeoutResolver,
            TimeoutFactory timeoutFactory,
            StoredRequestProcessor storedRequestProcessor,
            ApplicationSettings applicationSettings,
            IpAddressHelper ipAddressHelper,
            HookStageExecutor hookStageExecutor,
            @Autowired(required = false) DealsProcessor dealsProcessor,
            CountryCodeMapper countryCodeMapper,
            Clock clock) {

        final List<String> blacklistedAccounts = splitToList(blacklistedAccountsString);

        return new Ortb2RequestFactory(
                enforceValidAccount,
                blacklistedAccounts,
                uidsCookieService,
                requestValidator,
                auctionTimeoutResolver,
                timeoutFactory,
                storedRequestProcessor,
                applicationSettings,
                ipAddressHelper,
                hookStageExecutor,
                dealsProcessor,
                countryCodeMapper,
                clock);
    }

    @Bean
    AuctionRequestFactory auctionRequestFactory(
            @Value("${auction.max-request-size}") @Min(0) int maxRequestSize,
            Ortb2RequestFactory ortb2RequestFactory,
            StoredRequestProcessor storedRequestProcessor,
            ImplicitParametersExtractor implicitParametersExtractor,
            Ortb2ImplicitParametersResolver ortb2ImplicitParametersResolver,
            OrtbTypesResolver ortbTypesResolver,
            PrivacyEnforcementService privacyEnforcementService,
            TimeoutResolver auctionTimeoutResolver,
            DebugResolver debugResolver,
            JacksonMapper mapper) {

        return new AuctionRequestFactory(
                maxRequestSize,
                ortb2RequestFactory,
                storedRequestProcessor,
                implicitParametersExtractor,
                ortb2ImplicitParametersResolver,
                new InterstitialProcessor(),
                ortbTypesResolver,
                privacyEnforcementService,
                auctionTimeoutResolver,
                debugResolver,
                mapper);
    }

    @Bean
    IdGenerator bidIdGenerator(@Value("${auction.generate-bid-id}") boolean generateBidId) {
        return generateBidId
                ? new UUIDIdGenerator()
                : new NoneIdGenerator();
    }

    @Bean
    IdGenerator sourceIdGenerator(@Value("${auction.generate-source-tid}") boolean generateSourceTid) {
        return generateSourceTid
                ? new UUIDIdGenerator()
                : new NoneIdGenerator();
    }

    @Bean
    AmpRequestFactory ampRequestFactory(StoredRequestProcessor storedRequestProcessor,
                                        Ortb2RequestFactory ortb2RequestFactory,
                                        OrtbTypesResolver ortbTypesResolver,
                                        ImplicitParametersExtractor implicitParametersExtractor,
                                        Ortb2ImplicitParametersResolver ortb2ImplicitParametersResolver,
                                        FpdResolver fpdResolver,
                                        PrivacyEnforcementService privacyEnforcementService,
                                        TimeoutResolver auctionTimeoutResolver,
                                        DebugResolver debugResolver,
                                        JacksonMapper mapper) {

        return new AmpRequestFactory(
                storedRequestProcessor,
                ortb2RequestFactory,
                ortbTypesResolver,
                implicitParametersExtractor,
                ortb2ImplicitParametersResolver,
                fpdResolver,
                privacyEnforcementService,
                auctionTimeoutResolver,
                debugResolver,
                mapper);
    }

    @Bean
    VideoRequestFactory videoRequestFactory(
            @Value("${auction.max-request-size}") int maxRequestSize,
            @Value("${video.stored-request-required}") boolean enforceStoredRequest,
            @Value("${auction.video.escape-log-cache-regex:#{null}}") String escapeLogCacheRegex,
            VideoStoredRequestProcessor storedRequestProcessor,
            Ortb2RequestFactory ortb2RequestFactory,
            Ortb2ImplicitParametersResolver ortb2ImplicitParametersResolver,
            PrivacyEnforcementService privacyEnforcementService,
            TimeoutResolver auctionTimeoutResolver,
            DebugResolver debugResolver,
            JacksonMapper mapper) {

        return new VideoRequestFactory(
                maxRequestSize,
                enforceStoredRequest,
                escapeLogCacheRegex,
                ortb2RequestFactory,
                ortb2ImplicitParametersResolver,
                storedRequestProcessor,
                privacyEnforcementService,
                auctionTimeoutResolver,
                debugResolver,
                mapper);
    }

    @Bean
    VideoResponseFactory videoResponseFactory(JacksonMapper mapper) {
        return new VideoResponseFactory(new UUIDIdGenerator(), mapper);
    }

    @Bean
    VideoStoredRequestProcessor videoStoredRequestProcessor(
            @Value("${video.stored-request-required}") boolean enforceStoredRequest,
            @Value("${auction.blacklisted-accounts}") String blacklistedAccountsString,
            @Value("${video.stored-requests-timeout-ms}") long defaultTimeoutMs,
            @Value("${auction.ad-server-currency:#{null}}") String adServerCurrency,
            @Value("${default-request.file.path:#{null}}") String defaultBidRequestPath,
            FileSystem fileSystem,
            ApplicationSettings applicationSettings,
            VideoRequestValidator videoRequestValidator,
            Metrics metrics,
            TimeoutFactory timeoutFactory,
            TimeoutResolver auctionTimeoutResolver,
            JacksonMapper mapper,
            JsonMerger jsonMerger) {

        return new VideoStoredRequestProcessor(
                enforceStoredRequest,
                splitToList(blacklistedAccountsString),
                defaultTimeoutMs,
                adServerCurrency,
                defaultBidRequestPath,
                fileSystem,
                applicationSettings,
                videoRequestValidator,
                metrics,
                timeoutFactory,
                auctionTimeoutResolver,
                mapper,
                jsonMerger);
    }

    @Bean
    VideoRequestValidator videoRequestValidator() {
        return new VideoRequestValidator();
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
    HttpClientCircuitBreakerProperties httpClientCircuitBreakerProperties() {
        return new HttpClientCircuitBreakerProperties();
    }

    @Bean
    @Scope(scopeName = VertxContextScope.NAME, proxyMode = ScopedProxyMode.INTERFACES)
    @ConditionalOnProperty(prefix = "http-client.circuit-breaker", name = "enabled", havingValue = "true")
    CircuitBreakerSecuredHttpClient circuitBreakerSecuredHttpClient(
            Vertx vertx,
            Metrics metrics,
            HttpClientProperties httpClientProperties,
            @Qualifier("httpClientCircuitBreakerProperties")
                    HttpClientCircuitBreakerProperties circuitBreakerProperties,
            Clock clock) {

        final HttpClient httpClient = createBasicHttpClient(vertx, httpClientProperties);

        return new CircuitBreakerSecuredHttpClient(
                vertx,
                httpClient,
                metrics,
                circuitBreakerProperties.getOpeningThreshold(),
                circuitBreakerProperties.getOpeningIntervalMs(),
                circuitBreakerProperties.getClosingIntervalMs(),
                circuitBreakerProperties.getIdleExpireHours(),
                clock);
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
            BidderErrorNotifier bidderErrorNotifier,
            HttpBidderRequestEnricher requestEnricher,
            JacksonMapper mapper) {

        return new HttpBidderRequester(httpClient,
                bidderRequestCompletionTrackerFactory,
                bidderErrorNotifier,
                requestEnricher,
                mapper);
    }

    @Bean
    PrebidVersionProvider prebidVersionProvider(VersionInfo versionInfo) {
        return new PrebidVersionProvider(versionInfo.getVersion());
    }

    @Bean
    HttpBidderRequestEnricher httpBidderRequestEnricher(PrebidVersionProvider prebidVersionProvider) {
        return new HttpBidderRequestEnricher(prebidVersionProvider);
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
            VastModifier vastModifier,
            EventsService eventsService,
            StoredRequestProcessor storedRequestProcessor,
            WinningBidComparatorFactory winningBidComparatorFactory,
            IdGenerator bidIdGenerator,
            HookStageExecutor hookStageExecutor,
            CategoryMappingService categoryMappingService,
            @Value("${settings.targeting.truncate-attr-chars}") int truncateAttrChars,
            Clock clock,
            JacksonMapper mapper) {

        return new BidResponseCreator(
                cacheService,
                bidderCatalog,
                vastModifier,
                eventsService,
                storedRequestProcessor,
                winningBidComparatorFactory,
                bidIdGenerator,
                hookStageExecutor,
                categoryMappingService,
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
            SchainResolver schainResolver,
            DebugResolver debugResolver,
            HttpBidderRequester httpBidderRequester,
            ResponseBidValidator responseBidValidator,
            CurrencyConversionService currencyConversionService,
            BidResponseCreator bidResponseCreator,
            BidResponsePostProcessor bidResponsePostProcessor,
            HookStageExecutor hookStageExecutor,
            @Autowired(required = false) ApplicationEventService applicationEventService,
            HttpInteractionLogger httpInteractionLogger,
            Metrics metrics,
            Clock clock,
            JacksonMapper mapper,
            CriteriaLogManager criteriaLogManager) {

        return new ExchangeService(
                expectedCacheTimeMs,
                bidderCatalog,
                storedResponseProcessor,
                privacyEnforcementService,
                fpdResolver,
                schainResolver,
                debugResolver,
                httpBidderRequester,
                responseBidValidator,
                currencyConversionService,
                bidResponseCreator,
                bidResponsePostProcessor,
                hookStageExecutor,
                applicationEventService,
                httpInteractionLogger,
                metrics,
                clock,
                mapper,
                criteriaLogManager);
    }

    @Bean
    StoredRequestProcessor storedRequestProcessor(
            @Value("${auction.stored-requests-timeout-ms}") long defaultTimeoutMs,
            @Value("${default-request.file.path:#{null}}") String defaultBidRequestPath,
            @Value("${settings.generate-storedrequest-bidrequest-id}") boolean generateBidRequestId,
            FileSystem fileSystem,
            ApplicationSettings applicationSettings,
            Metrics metrics,
            TimeoutFactory timeoutFactory,
            JacksonMapper mapper,
            JsonMerger jsonMerger) {

        return new StoredRequestProcessor(
                defaultTimeoutMs,
                defaultBidRequestPath,
                generateBidRequestId,
                fileSystem,
                applicationSettings,
                new UUIDIdGenerator(),
                metrics,
                timeoutFactory,
                mapper,
                jsonMerger);
    }

    @Bean
    WinningBidComparatorFactory winningBidComparatorFactory() {
        return new WinningBidComparatorFactory();
    }

    @Bean
    StoredResponseProcessor storedResponseProcessor(ApplicationSettings applicationSettings,
                                                    JacksonMapper mapper) {

        return new StoredResponseProcessor(applicationSettings, mapper);
    }

    @Bean
    PrivacyEnforcementService privacyEnforcementService(
            BidderCatalog bidderCatalog,
            PrivacyExtractor privacyExtractor,
            TcfDefinerService tcfDefinerService,
            ImplicitParametersExtractor implicitParametersExtractor,
            IpAddressHelper ipAddressHelper,
            Metrics metrics,
            CountryCodeMapper countryCodeMapper,
            @Value("${ccpa.enforce}") boolean ccpaEnforce,
            @Value("${lmt.enforce}") boolean lmtEnforce) {

        return new PrivacyEnforcementService(
                bidderCatalog,
                privacyExtractor,
                tcfDefinerService,
                implicitParametersExtractor,
                ipAddressHelper,
                metrics,
                countryCodeMapper,
                ccpaEnforce,
                lmtEnforce);
    }

    @Bean
    PrivacyExtractor privacyExtractor() {
        return new PrivacyExtractor();
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
    ResponseBidValidator responseValidator(
            @Value("${auction.validations.banner-creative-max-size}") BidValidationEnforcement bannerMaxSizeEnforcement,
            @Value("${auction.validations.secure-markup}") BidValidationEnforcement secureMarkupEnforcement,
            Metrics metrics,
            JacksonMapper mapper,
            @Value("${deals.enabled}") boolean dealsEnabled) {

        return new ResponseBidValidator(bannerMaxSizeEnforcement, secureMarkupEnforcement, metrics, mapper,
                dealsEnabled);
    }

    @Bean
    CriteriaLogManager criteriaLogManager(JacksonMapper mapper) {
        return new CriteriaLogManager(mapper);
    }

    @Bean
    CriteriaManager criteriaManager(CriteriaLogManager criteriaLogManager, Vertx vertx) {
        return new CriteriaManager(criteriaLogManager, vertx);
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
            @Value("${currency-converter.external-rates.stale-period-ms:#{null}}") Long stalePeriodMs,
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
                stalePeriodMs,
                vertx,
                httpClient,
                metrics,
                clock,
                mapper);
    }

    @Bean
    HttpInteractionLogger httpInteractionLogger(JacksonMapper mapper) {
        return new HttpInteractionLogger(mapper);
    }

    @Bean
    LoggerControlKnob loggerControlKnob(Vertx vertx) {
        return new LoggerControlKnob(vertx);
    }

    private static List<String> splitToList(String listAsString) {
        return listAsString != null
                ? Stream.of(listAsString.split(","))
                .map(String::trim)
                .collect(Collectors.toList())
                : null;
    }
}
