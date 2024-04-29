package org.prebid.server.spring.config.server.application;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.activity.infrastructure.creator.ActivityInfrastructureCreator;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.VideoResponseFactory;
import org.prebid.server.auction.gpp.CookieSyncGppService;
import org.prebid.server.auction.gpp.SetuidGppService;
import org.prebid.server.auction.privacy.contextfactory.CookieSyncPrivacyContextFactory;
import org.prebid.server.auction.privacy.contextfactory.SetuidPrivacyContextFactory;
import org.prebid.server.auction.requestfactory.AmpRequestFactory;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
import org.prebid.server.auction.requestfactory.VideoRequestFactory;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cookie.CookieDeprecationService;
import org.prebid.server.cookie.CookieSyncService;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.handler.BidderParamHandler;
import org.prebid.server.handler.CookieSyncHandler;
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
import org.prebid.server.handler.info.filters.BaseOnlyBidderInfoFilterStrategy;
import org.prebid.server.handler.info.filters.BidderInfoFilterStrategy;
import org.prebid.server.handler.info.filters.EnabledOnlyBidderInfoFilterStrategy;
import org.prebid.server.handler.openrtb2.AmpHandler;
import org.prebid.server.handler.openrtb2.VideoHandler;
import org.prebid.server.health.HealthChecker;
import org.prebid.server.health.PeriodicHealthChecker;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.optout.GoogleRecaptchaVerifier;
import org.prebid.server.privacy.HostVendorTcfDefinerService;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.spring.config.server.admin.AdminResourcesBinder;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.validation.BidderParamValidator;
import org.prebid.server.version.PrebidVersionProvider;
import org.prebid.server.vertx.verticles.VerticleDefinition;
import org.prebid.server.vertx.verticles.server.ServerVerticle;
import org.prebid.server.vertx.verticles.server.application.ApplicationResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Configuration
public class ApplicationServerConfiguration {

    @Value("${logging.sampling-rate:0.01}")
    private double logSamplingRate;

    @Bean
    @ConditionalOnProperty(name = "server.http.enabled", havingValue = "true")
    VerticleDefinition httpApplicationServerVerticleDefinition(
            HttpServerOptions httpServerOptions,
            @Value("#{'${http.port:${server.http.port}}'}") Integer port,
            Router applicationServerRouter,
            ExceptionHandler exceptionHandler,
            @Value("#{'${vertx.http-server-instances:${server.http.server-instances}}'}") Integer instances) {

        return VerticleDefinition.ofMultiInstance(
                () -> new ServerVerticle(
                        "Application Http Server",
                        httpServerOptions,
                        SocketAddress.inetSocketAddress(port, "0.0.0.0"),
                        applicationServerRouter,
                        exceptionHandler),
                instances);
    }

    @Bean
    @ConditionalOnProperty(name = "server.unix-socket.enabled", havingValue = "true")
    VerticleDefinition unixSocketApplicationServerVerticleDefinition(
            HttpServerOptions httpServerOptions,
            @Value("${server.unix-socket.path}") String path,
            Router applicationServerRouter,
            ExceptionHandler exceptionHandler,
            @Value("${server.unix-socket.server-instances}") Integer instances) {

        return VerticleDefinition.ofMultiInstance(
                () -> new ServerVerticle(
                        "Application Unix Socket Server",
                        httpServerOptions,
                        SocketAddress.domainSocketAddress(path),
                        applicationServerRouter,
                        exceptionHandler),
                instances);
    }

    // TODO: remove support for properties with http prefix after transition period
    @Bean
    HttpServerOptions httpServerOptions(
            @Value("#{'${http.max-headers-size:${server.max-headers-size:}}'}") int maxHeaderSize,
            @Value("#{'${http.max-initial-line-length:${server.max-initial-line-length:}}'}") int maxInitialLineLength,
            @Value("#{'${http.ssl:${server.ssl:}}'}") boolean ssl,
            @Value("#{'${http.jks-path:${server.jks-path:}}'}") String jksPath,
            @Value("#{'${http.jks-password:${server.jks-password:}}'}") String jksPassword,
            @Value("#{'${http.idle-timeout:${server.idle-timeout}}'}") int idleTimeout,
            @Value("${server.enable-quickack:#{null}}") Optional<Boolean> enableQuickAck,
            @Value("${server.enable-reuseport:#{null}}") Optional<Boolean> enableReusePort) {

        final HttpServerOptions httpServerOptions = new HttpServerOptions()
                .setHandle100ContinueAutomatically(true)
                .setMaxInitialLineLength(maxInitialLineLength)
                .setMaxHeaderSize(maxHeaderSize)
                .setCompressionSupported(true)
                .setDecompressionSupported(true)
                .setIdleTimeout(idleTimeout); // kick off long processing requests, value in seconds
        enableQuickAck.ifPresent(httpServerOptions::setTcpQuickAck);
        enableReusePort.ifPresent(httpServerOptions::setReusePort);
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

    @Bean
    Router applicationServerRouter(Vertx vertx,
                                   BodyHandler bodyHandler,
                                   NoCacheHandler noCacheHandler,
                                   CorsHandler corsHandler,
                                   List<ApplicationResource> resources,
                                   AdminResourcesBinder applicationPortAdminResourcesBinder,
                                   StaticHandler staticHandler) {

        final Router router = Router.router(vertx);
        router.route().handler(bodyHandler);
        router.route().handler(noCacheHandler);
        router.route().handler(corsHandler);

        resources.forEach(resource ->
                resource.endpoints().forEach(endpoint ->
                        router.route(endpoint.getMethod(), endpoint.getPath()).handler(resource)));

        applicationPortAdminResourcesBinder.bind(router);

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
                logSamplingRate,
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
                mapper,
                logSamplingRate);
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
            @Value("${cookie-sync.default-timeout-ms}") int defaultTimeoutMs,
            UidsCookieService uidsCookieService,
            CookieSyncGppService cookieSyncGppProcessor,
            CookieDeprecationService cookieDeprecationService,
            ActivityInfrastructureCreator activityInfrastructureCreator,
            ApplicationSettings applicationSettings,
            CookieSyncService cookieSyncService,
            CookieSyncPrivacyContextFactory cookieSyncPrivacyContextFactory,
            AnalyticsReporterDelegator analyticsReporterDelegator,
            Metrics metrics,
            TimeoutFactory timeoutFactory,
            JacksonMapper mapper) {

        return new CookieSyncHandler(
                defaultTimeoutMs,
                logSamplingRate,
                uidsCookieService,
                cookieDeprecationService,
                cookieSyncGppProcessor,
                activityInfrastructureCreator,
                cookieSyncService,
                applicationSettings,
                cookieSyncPrivacyContextFactory,
                analyticsReporterDelegator,
                metrics,
                timeoutFactory,
                mapper);
    }

    @Bean
    SetuidHandler setuidHandler(
            @Value("${setuid.default-timeout-ms}") int defaultTimeoutMs,
            UidsCookieService uidsCookieService,
            ApplicationSettings applicationSettings,
            BidderCatalog bidderCatalog,
            SetuidPrivacyContextFactory setuidPrivacyContextFactory,
            SetuidGppService setuidGppService,
            ActivityInfrastructureCreator activityInfrastructureCreator,
            HostVendorTcfDefinerService tcfDefinerService,
            AnalyticsReporterDelegator analyticsReporter,
            Metrics metrics,
            TimeoutFactory timeoutFactory) {

        return new SetuidHandler(
                defaultTimeoutMs,
                uidsCookieService,
                applicationSettings,
                bidderCatalog,
                setuidPrivacyContextFactory,
                setuidGppService,
                activityInfrastructureCreator,
                tcfDefinerService,
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
    BidderInfoFilterStrategy enabledOnlyBidderInfoFilterStrategy(BidderCatalog bidderCatalog) {
        return new EnabledOnlyBidderInfoFilterStrategy(bidderCatalog);
    }

    @Bean
    BidderInfoFilterStrategy baseOnlyBidderInfoFilterStrategy(BidderCatalog bidderCatalog) {
        return new BaseOnlyBidderInfoFilterStrategy(bidderCatalog);
    }

    @Bean
    BiddersHandler biddersHandler(BidderCatalog bidderCatalog,
                                  List<BidderInfoFilterStrategy> filterStrategies,
                                  JacksonMapper mapper) {
        return new BiddersHandler(bidderCatalog, filterStrategies, mapper);
    }

    @Bean
    BidderDetailsHandler bidderDetailsHandler(BidderCatalog bidderCatalog, JacksonMapper mapper) {
        return new BidderDetailsHandler(bidderCatalog, mapper);
    }

    @Bean
    NotificationEventHandler notificationEventHandler(ActivityInfrastructureCreator activityInfrastructureCreator,
                                                      AnalyticsReporterDelegator analyticsReporterDelegator,
                                                      TimeoutFactory timeoutFactory,
                                                      ApplicationSettings applicationSettings,
                                                      @Value("${event.default-timeout-ms}") long defaultTimeoutMillis) {

        return new NotificationEventHandler(
                activityInfrastructureCreator,
                analyticsReporterDelegator,
                timeoutFactory,
                applicationSettings,
                defaultTimeoutMillis);
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
}
