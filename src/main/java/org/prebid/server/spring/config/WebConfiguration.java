package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.analytics.AnalyticsReporterDelegator;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.VideoResponseFactory;
import org.prebid.server.auction.requestfactory.AmpRequestFactory;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
import org.prebid.server.auction.requestfactory.VideoRequestFactory;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.deals.UserService;
import org.prebid.server.deals.events.ApplicationEventService;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.handler.BidderParamHandler;
import org.prebid.server.handler.CookieSyncHandler;
import org.prebid.server.handler.CustomizedAdminEndpoint;
import org.prebid.server.handler.ExceptionHandler;
import org.prebid.server.handler.GetuidsHandler;
import org.prebid.server.handler.NoCacheHandler;
import org.prebid.server.handler.NotificationEventHandler;
import org.prebid.server.handler.OptoutHandler;
import org.prebid.server.handler.SetuidHandler;
import org.prebid.server.handler.StatusHandler;
import org.prebid.server.handler.VtrackHandler;
import org.prebid.server.handler.info.BidderDetailsHandler;
import org.prebid.server.handler.info.BiddersHandler;
import org.prebid.server.handler.openrtb2.AmpHandler;
import org.prebid.server.handler.openrtb2.VideoHandler;
import org.prebid.server.health.HealthChecker;
import org.prebid.server.health.PeriodicHealthChecker;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.optout.GoogleRecaptchaVerifier;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.validation.BidderParamValidator;
import org.prebid.server.version.PrebidVersionProvider;
import org.prebid.server.vertx.ContextRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class WebConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(WebConfiguration.class);

    @Autowired
    private ContextRunner contextRunner;

    @Value("${vertx.http-server-instances}")
    private int httpServerNum;

    @Autowired
    private Vertx vertx;

    @Autowired
    private HttpServerOptions httpServerOptions;

    @Autowired
    private ExceptionHandler exceptionHandler;

    @Autowired
    @Qualifier("router")
    private Router router;

    @Value("${http.port}")
    private int httpPort;

    @PostConstruct
    public void startHttpServer() {
        logger.info("Starting {0} instances of Http Server to serve requests on port {1,number,#}", httpServerNum,
                httpPort);

        contextRunner.<HttpServer>runOnNewContext(httpServerNum, promise ->
                vertx.createHttpServer(httpServerOptions)
                        .exceptionHandler(exceptionHandler)
                        .requestHandler(router)
                        .listen(httpPort, promise));

        logger.info("Successfully started {0} instances of Http Server", httpServerNum);
    }

    @Bean
    HttpServerOptions httpServerOptions(@Value("${http.max-headers-size}") int maxHeaderSize,
                                        @Value("${http.ssl}") boolean ssl,
                                        @Value("${http.jks-path}") String jksPath,
                                        @Value("${http.jks-password}") String jksPassword) {

        final HttpServerOptions httpServerOptions = new HttpServerOptions()
                .setHandle100ContinueAutomatically(true)
                .setMaxHeaderSize(maxHeaderSize)
                .setCompressionSupported(true)
                .setDecompressionSupported(true)
                .setIdleTimeout(10); // kick off long processing requests

        if (ssl) {
            final JksOptions jksOptions = new JksOptions()
                    .setPath(jksPath)
                    .setPassword(jksPassword);

            httpServerOptions
                    .setSsl(true)
                    .setKeyStoreOptions(jksOptions);
        }

        return httpServerOptions;
    }

    @Bean
    ExceptionHandler exceptionHandler(Metrics metrics) {
        return ExceptionHandler.create(metrics);
    }

    @Bean("router")
    Router router(BodyHandler bodyHandler,
                  NoCacheHandler noCacheHandler,
                  CorsHandler corsHandler,
                  org.prebid.server.handler.openrtb2.AuctionHandler openrtbAuctionHandler,
                  AmpHandler openrtbAmpHandler,
                  VideoHandler openrtbVideoHandler,
                  StatusHandler statusHandler,
                  CookieSyncHandler cookieSyncHandler,
                  SetuidHandler setuidHandler,
                  GetuidsHandler getuidsHandler,
                  VtrackHandler vtrackHandler,
                  OptoutHandler optoutHandler,
                  BidderParamHandler bidderParamHandler,
                  BiddersHandler biddersHandler,
                  BidderDetailsHandler bidderDetailsHandler,
                  NotificationEventHandler notificationEventHandler,
                  List<CustomizedAdminEndpoint> customizedAdminEndpoints,
                  StaticHandler staticHandler) {

        final Router router = Router.router(vertx);
        router.route().handler(bodyHandler);
        router.route().handler(noCacheHandler);
        router.route().handler(corsHandler);
        router.post("/openrtb2/auction").handler(openrtbAuctionHandler);
        router.get("/openrtb2/amp").handler(openrtbAmpHandler);
        router.post("/openrtb2/video").handler(openrtbVideoHandler);
        router.get("/status").handler(statusHandler);
        router.post("/cookie_sync").handler(cookieSyncHandler);
        router.get("/setuid").handler(setuidHandler);
        router.get("/getuids").handler(getuidsHandler);
        router.post("/vtrack").handler(vtrackHandler);
        router.post("/optout").handler(optoutHandler);
        router.get("/optout").handler(optoutHandler);
        router.get("/bidders/params").handler(bidderParamHandler);
        router.get("/info/bidders").handler(biddersHandler);
        router.get("/info/bidders/:bidderName").handler(bidderDetailsHandler);
        router.get("/event").handler(notificationEventHandler);

        customizedAdminEndpoints.stream()
                .filter(CustomizedAdminEndpoint::isOnApplicationPort)
                .forEach(customizedAdminEndpoint -> customizedAdminEndpoint.router(router));

        router.get("/static/*").handler(staticHandler);
        router.get("/").handler(staticHandler); // serves index.html by default

        return router;
    }

    @Bean
    NoCacheHandler noCacheHandler() {
        return NoCacheHandler.create();
    }

    @Bean
    CorsHandler corsHandler() {
        return CorsHandler.create(".*")
                .allowCredentials(true)
                .allowedHeaders(new HashSet<>(Arrays.asList(
                        HttpUtil.ORIGIN_HEADER.toString(),
                        HttpUtil.ACCEPT_HEADER.toString(),
                        HttpUtil.CONTENT_TYPE_HEADER.toString(),
                        HttpUtil.X_REQUESTED_WITH_HEADER.toString())))
                .allowedMethods(new HashSet<>(Arrays.asList(HttpMethod.GET, HttpMethod.POST, HttpMethod.HEAD,
                        HttpMethod.OPTIONS)));
    }

    @Bean
    org.prebid.server.handler.openrtb2.AuctionHandler openrtbAuctionHandler(
            ExchangeService exchangeService,
            AuctionRequestFactory auctionRequestFactory,
            AnalyticsReporterDelegator analyticsReporter,
            Metrics metrics,
            Clock clock,
            HttpInteractionLogger httpInteractionLogger,
            PrebidVersionProvider prebidVersionProvider,
            JacksonMapper mapper) {

        return new org.prebid.server.handler.openrtb2.AuctionHandler(
                auctionRequestFactory,
                exchangeService,
                analyticsReporter,
                metrics,
                clock,
                httpInteractionLogger,
                prebidVersionProvider,
                mapper);
    }

    @Bean
    AmpHandler openrtbAmpHandler(
            AmpRequestFactory ampRequestFactory,
            ExchangeService exchangeService,
            AnalyticsReporterDelegator analyticsReporter,
            Metrics metrics,
            Clock clock,
            BidderCatalog bidderCatalog,
            AmpProperties ampProperties,
            AmpResponsePostProcessor ampResponsePostProcessor,
            HttpInteractionLogger httpInteractionLogger,
            PrebidVersionProvider prebidVersionProvider,
            JacksonMapper mapper) {

        return new AmpHandler(
                ampRequestFactory,
                exchangeService,
                analyticsReporter,
                metrics,
                clock,
                bidderCatalog,
                ampProperties.getCustomTargetingSet(),
                ampResponsePostProcessor,
                httpInteractionLogger,
                prebidVersionProvider,
                mapper);
    }

    @Bean
    VideoHandler openrtbVideoHandler(
            VideoRequestFactory videoRequestFactory,
            VideoResponseFactory videoResponseFactory,
            ExchangeService exchangeService,
            CacheService cacheService,
            AnalyticsReporterDelegator analyticsReporter,
            Metrics metrics,
            Clock clock,
            PrebidVersionProvider prebidVersionProvider,
            JacksonMapper mapper) {

        return new VideoHandler(
                videoRequestFactory,
                videoResponseFactory,
                exchangeService,
               cacheService, analyticsReporter,
                metrics,
                clock,
                prebidVersionProvider,
                mapper);
    }

    @Bean
    StatusHandler statusHandler(List<HealthChecker> healthCheckers, JacksonMapper mapper) {
        healthCheckers.stream()
                .filter(PeriodicHealthChecker.class::isInstance)
                .map(PeriodicHealthChecker.class::cast)
                .forEach(PeriodicHealthChecker::initialize);
        return new StatusHandler(healthCheckers, mapper);
    }

    @Bean
    CookieSyncHandler cookieSyncHandler(
            @Value("${external-url}") String externalUrl,
            @Value("${cookie-sync.default-timeout-ms}") int defaultTimeoutMs,
            UidsCookieService uidsCookieService,
            ApplicationSettings applicationSettings,
            BidderCatalog bidderCatalog,
            CoopSyncPriorities coopSyncPriorities,
            TcfDefinerService tcfDefinerService,
            PrivacyEnforcementService privacyEnforcementService,
            @Value("${gdpr.host-vendor-id:#{null}}") Integer hostVendorId,
            @Value("${cookie-sync.coop-sync.default}") boolean defaultCoopSync,
            AnalyticsReporterDelegator analyticsReporterDelegator,
            Metrics metrics,
            TimeoutFactory timeoutFactory,
            JacksonMapper mapper) {
        return new CookieSyncHandler(externalUrl, defaultTimeoutMs, uidsCookieService, applicationSettings,
                bidderCatalog, tcfDefinerService, privacyEnforcementService, hostVendorId,
                defaultCoopSync, coopSyncPriorities.getPri(), analyticsReporterDelegator, metrics, timeoutFactory,
                mapper);
    }

    @Bean
    SetuidHandler setuidHandler(
            @Value("${setuid.default-timeout-ms}") int defaultTimeoutMs,
            UidsCookieService uidsCookieService,
            ApplicationSettings applicationSettings,
            BidderCatalog bidderCatalog,
            PrivacyEnforcementService privacyEnforcementService,
            TcfDefinerService tcfDefinerService,
            @Value("${gdpr.host-vendor-id:#{null}}") Integer hostVendorId,
            AnalyticsReporterDelegator analyticsReporter,
            Metrics metrics,
            TimeoutFactory timeoutFactory) {

        return new SetuidHandler(
                defaultTimeoutMs,
                uidsCookieService,
                applicationSettings,
                bidderCatalog,
                privacyEnforcementService,
                tcfDefinerService,
                hostVendorId,
                analyticsReporter,
                metrics,
                timeoutFactory);
    }

    @Bean
    GetuidsHandler getuidsHandler(UidsCookieService uidsCookieService, JacksonMapper mapper) {
        return new GetuidsHandler(uidsCookieService, mapper);
    }

    @Bean
    VtrackHandler vtrackHandler(
            @Value("${vtrack.default-timeout-ms}") int defaultTimeoutMs,
            @Value("${vtrack.allow-unknown-bidder}") boolean allowUnknownBidder,
            @Value("${vtrack.modify-vast-for-unknown-bidder}") boolean modifyVastForUnknownBidder,
            ApplicationSettings applicationSettings,
            BidderCatalog bidderCatalog,
            CacheService cacheService,
            TimeoutFactory timeoutFactory,
            JacksonMapper mapper) {

        return new VtrackHandler(
                defaultTimeoutMs,
                allowUnknownBidder,
                modifyVastForUnknownBidder,
                applicationSettings,
                bidderCatalog,
                cacheService,
                timeoutFactory,
                mapper);
    }

    @Bean
    OptoutHandler optoutHandler(
            @Value("${external-url}") String externalUrl,
            @Value("${host-cookie.opt-out-url}") String optoutUrl,
            @Value("${host-cookie.opt-in-url}") String optinUrl,
            GoogleRecaptchaVerifier googleRecaptchaVerifier,
            UidsCookieService uidsCookieService) {

        return new OptoutHandler(
                googleRecaptchaVerifier,
                uidsCookieService,
                OptoutHandler.getOptoutRedirectUrl(externalUrl),
                HttpUtil.validateUrl(optoutUrl),
                HttpUtil.validateUrl(optinUrl));
    }

    @Bean
    BidderParamHandler bidderParamHandler(BidderParamValidator bidderParamValidator) {
        return new BidderParamHandler(bidderParamValidator);
    }

    @Bean
    BiddersHandler biddersHandler(BidderCatalog bidderCatalog, JacksonMapper mapper) {
        return new BiddersHandler(bidderCatalog, mapper);
    }

    @Bean
    BidderDetailsHandler bidderDetailsHandler(BidderCatalog bidderCatalog, JacksonMapper mapper) {
        return new BidderDetailsHandler(bidderCatalog, mapper);
    }

    @Bean
    NotificationEventHandler notificationEventHandler(
            UidsCookieService uidsCookieService,
            @Autowired(required = false) ApplicationEventService applicationEventService,
            @Autowired(required = false) UserService userService,
            AnalyticsReporterDelegator analyticsReporterDelegator,
            TimeoutFactory timeoutFactory,
            ApplicationSettings applicationSettings,
            @Value("${event.default-timeout-ms}") long defaultTimeoutMillis,
            @Value("${deals.enabled}") boolean dealsEnabled) {

        return new NotificationEventHandler(
                uidsCookieService,
                applicationEventService,
                userService,
                analyticsReporterDelegator,
                timeoutFactory,
                applicationSettings,
                defaultTimeoutMillis,
                dealsEnabled);
    }

    @Bean
    StaticHandler staticHandler() {
        return StaticHandler.create("static").setCachingEnabled(false);
    }

    @Component
    @ConfigurationProperties(prefix = "amp")
    @Data
    @NoArgsConstructor
    private static class AmpProperties {

        private List<String> customTargeting = new ArrayList<>();

        Set<String> getCustomTargetingSet() {
            return new HashSet<>(customTargeting);
        }
    }

    @Component
    @ConfigurationProperties(prefix = "cookie-sync.coop-sync")
    @Data
    @NoArgsConstructor
    private static class CoopSyncPriorities {

        private List<Collection<String>> pri;
    }
}
