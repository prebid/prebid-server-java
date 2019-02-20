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
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.analytics.CompositeAnalyticsReporter;
import org.prebid.server.auction.AmpRequestFactory;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.AuctionRequestFactory;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.PreBidRequestContextFactory;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.handler.AuctionHandler;
import org.prebid.server.handler.BidderParamHandler;
import org.prebid.server.handler.CookieSyncHandler;
import org.prebid.server.handler.CurrencyRatesHandler;
import org.prebid.server.handler.ExceptionHandler;
import org.prebid.server.handler.NoCacheHandler;
import org.prebid.server.handler.OptoutHandler;
import org.prebid.server.handler.SettingsCacheNotificationHandler;
import org.prebid.server.handler.SetuidHandler;
import org.prebid.server.handler.StatusHandler;
import org.prebid.server.handler.VersionHandler;
import org.prebid.server.handler.info.BidderDetailsHandler;
import org.prebid.server.handler.info.BiddersHandler;
import org.prebid.server.handler.openrtb2.AmpHandler;
import org.prebid.server.metric.Metrics;
import org.prebid.server.optout.GoogleRecaptchaVerifier;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.SettingsCache;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.validation.BidderParamValidator;
import org.prebid.server.vertx.ContextRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
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
    private Router router;

    @Value("${http.port}")
    private int httpPort;

    @PostConstruct
    public void startHttpServer() {
        logger.info("Starting {0} instances of Http Server to serve requests on port {1,number,#}", httpServerNum,
                httpPort);

        contextRunner.<HttpServer>runOnNewContext(httpServerNum, future ->
                vertx.createHttpServer(httpServerOptions)
                        .exceptionHandler(exceptionHandler)
                        .requestHandler(router)
                        .listen(httpPort, future));

        logger.info("Successfully started {0} instances of Http Server", httpServerNum);
    }

    @Bean
    HttpServerOptions httpServerOptions(@Value("${http.ssl}") boolean ssl,
                                        @Value("${http.jks-path}") String jksPath,
                                        @Value("${http.jks-password}") String jksPassword) {
        final HttpServerOptions httpServerOptions = new HttpServerOptions()
                .setHandle100ContinueAutomatically(true)
                .setCompressionSupported(true)
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

    @Bean
    Router router(CookieHandler cookieHandler,
                  BodyHandler bodyHandler,
                  NoCacheHandler noCacheHandler,
                  CorsHandler corsHandler,
                  AuctionHandler auctionHandler,
                  org.prebid.server.handler.openrtb2.AuctionHandler openrtbAuctionHandler,
                  AmpHandler openrtbAmpHandler,
                  StatusHandler statusHandler,
                  CookieSyncHandler cookieSyncHandler,
                  SetuidHandler setuidHandler,
                  OptoutHandler optoutHandler,
                  BidderParamHandler bidderParamHandler,
                  BiddersHandler biddersHandler,
                  BidderDetailsHandler bidderDetailsHandler,
                  StaticHandler staticHandler) {

        final Router router = Router.router(vertx);
        router.route().handler(cookieHandler);
        router.route().handler(bodyHandler);
        router.route().handler(noCacheHandler);
        router.route().handler(corsHandler);
        router.post("/auction").handler(auctionHandler);
        router.post("/openrtb2/auction").handler(openrtbAuctionHandler);
        router.get("/openrtb2/amp").handler(openrtbAmpHandler);
        router.get("/status").handler(statusHandler);
        router.post("/cookie_sync").handler(cookieSyncHandler);
        router.get("/setuid").handler(setuidHandler);
        router.post("/optout").handler(optoutHandler);
        router.get("/optout").handler(optoutHandler);
        router.get("/bidders/params").handler(bidderParamHandler);
        router.get("/info/bidders").handler(biddersHandler);
        router.get("/info/bidders/:bidderName").handler(bidderDetailsHandler);
        router.get("/static/*").handler(staticHandler);
        router.get("/").handler(staticHandler); // serves index.html by default

        return router;
    }

    @Bean
    CookieHandler cookieHandler() {
        return CookieHandler.create();
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
    AuctionHandler auctionHandler(
            ApplicationSettings applicationSettings,
            BidderCatalog bidderCatalog,
            PreBidRequestContextFactory preBidRequestContextFactory,
            CacheService cacheService,
            Metrics metrics,
            HttpAdapterConnector httpAdapterConnector,
            Clock clock,
            GdprService gdprService,
            @Value("${gdpr.host-vendor-id:#{null}}") Integer hostVendorId,
            @Value("${gdpr.geolocation.enabled}") boolean useGeoLocation) {

        return new AuctionHandler(applicationSettings, bidderCatalog, preBidRequestContextFactory, cacheService,
                metrics, httpAdapterConnector, clock, gdprService, hostVendorId, useGeoLocation);
    }

    @Bean
    org.prebid.server.handler.openrtb2.AuctionHandler openrtbAuctionHandler(
            ExchangeService exchangeService,
            AuctionRequestFactory auctionRequestFactory,
            UidsCookieService uidsCookieService,
            CompositeAnalyticsReporter analyticsReporter,
            Metrics metrics,
            Clock clock,
            TimeoutFactory timeoutFactory) {

        return new org.prebid.server.handler.openrtb2.AuctionHandler(exchangeService, auctionRequestFactory,
                uidsCookieService, analyticsReporter, metrics, clock, timeoutFactory);
    }

    @Bean
    AmpHandler openrtbAmpHandler(
            AmpRequestFactory ampRequestFactory,
            ExchangeService exchangeService,
            UidsCookieService uidsCookieService,
            AmpProperties ampProperties,
            BidderCatalog bidderCatalog,
            CompositeAnalyticsReporter analyticsReporter,
            AmpResponsePostProcessor ampResponsePostProcessor,
            Metrics metrics,
            Clock clock,
            TimeoutFactory timeoutFactory) {

        return new AmpHandler(ampRequestFactory, exchangeService, uidsCookieService,
                ampProperties.getCustomTargetingSet(), bidderCatalog, analyticsReporter, ampResponsePostProcessor,
                metrics, clock, timeoutFactory);
    }

    @Bean
    StatusHandler statusHandler(@Value("${status-response:#{null}}") String statusResponse) {
        return new StatusHandler(statusResponse);
    }

    @Bean
    CookieSyncHandler cookieSyncHandler(
            @Value("${external-url}") String externalUrl,
            @Value("${cookie-sync.default-timeout-ms}") int defaultTimeoutMs,
            UidsCookieService uidsCookieService,
            BidderCatalog bidderCatalog,
            GdprService gdprService,
            @Value("${gdpr.host-vendor-id:#{null}}") Integer hostVendorId,
            @Value("${gdpr.geolocation.enabled}") boolean useGeoLocation,
            CompositeAnalyticsReporter analyticsReporter,
            Metrics metrics,
            TimeoutFactory timeoutFactory) {

        return new CookieSyncHandler(externalUrl, defaultTimeoutMs, uidsCookieService, bidderCatalog, gdprService,
                hostVendorId, useGeoLocation, analyticsReporter, metrics, timeoutFactory);
    }

    @Bean
    SetuidHandler setuidHandler(
            @Value("${setuid.default-timeout-ms}") int defaultTimeoutMs,
            UidsCookieService uidsCookieService,
            GdprService gdprService,
            @Value("${gdpr.host-vendor-id:#{null}}") Integer hostVendorId,
            @Value("${gdpr.geolocation.enabled}") boolean useGeoLocation,
            CompositeAnalyticsReporter analyticsReporter,
            Metrics metrics,
            TimeoutFactory timeoutFactory) {

        return new SetuidHandler(defaultTimeoutMs, uidsCookieService, gdprService, hostVendorId, useGeoLocation,
                analyticsReporter, metrics, timeoutFactory);
    }

    @Bean
    OptoutHandler optoutHandler(
            @Value("${external-url}") String externalUrl,
            @Value("${host-cookie.opt-out-url}") String optoutUrl,
            @Value("${host-cookie.opt-in-url}") String optinUrl,
            GoogleRecaptchaVerifier googleRecaptchaVerifier,
            UidsCookieService uidsCookieService) {

        return new OptoutHandler(googleRecaptchaVerifier, uidsCookieService,
                OptoutHandler.getOptoutRedirectUrl(externalUrl), HttpUtil.validateUrl(optoutUrl),
                HttpUtil.validateUrl(optinUrl));
    }

    @Bean
    BidderParamHandler bidderParamHandler(BidderParamValidator bidderParamValidator) {
        return new BidderParamHandler(bidderParamValidator);
    }

    @Bean
    BiddersHandler biddersHandler(BidderCatalog bidderCatalog) {
        return new BiddersHandler(bidderCatalog);
    }

    @Bean
    BidderDetailsHandler bidderDetailsHandler(BidderCatalog bidderCatalog) {
        return new BidderDetailsHandler(bidderCatalog);
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

    @Configuration
    @ConditionalOnProperty(prefix = "admin", name = "port")
    static class AdminServerConfiguration {

        private static final Logger logger = LoggerFactory.getLogger(AdminServerConfiguration.class);

        @Autowired
        private ContextRunner contextRunner;

        @Autowired
        private Vertx vertx;

        @Autowired
        private BodyHandler bodyHandler;

        @Autowired
        private VersionHandler versionHandler;

        @Autowired
        private CurrencyRatesHandler currencyRatesHandler;

        @Autowired(required = false)
        private SettingsCacheNotificationHandler cacheNotificationHandler;

        @Autowired(required = false)
        private SettingsCacheNotificationHandler ampCacheNotificationHandler;

        @Value("${admin.port}")
        private int adminPort;

        @Bean
        @ConditionalOnProperty(prefix = "settings.in-memory-cache", name = "notification-endpoints-enabled",
                havingValue = "true")
        SettingsCacheNotificationHandler cacheNotificationHandler(SettingsCache settingsCache) {
            return new SettingsCacheNotificationHandler(settingsCache);
        }

        @Bean
        @ConditionalOnProperty(prefix = "settings.in-memory-cache", name = "notification-endpoints-enabled",
                havingValue = "true")
        SettingsCacheNotificationHandler ampCacheNotificationHandler(SettingsCache ampSettingsCache) {
            return new SettingsCacheNotificationHandler(ampSettingsCache);
        }

        @Bean
        VersionHandler versionHandler() {
            return VersionHandler.create("git-revision.json");
        }

        @Bean
        CurrencyRatesHandler currencyRatesHandler(CurrencyConversionService currencyConversionRates) {
            return new CurrencyRatesHandler(currencyConversionRates);
        }

        @PostConstruct
        public void startAdminServer() {
            logger.info("Starting Admin Server to serve requests on port {0,number,#}", adminPort);

            final Router router = Router.router(vertx);
            router.route().handler(bodyHandler);
            router.route("/version").handler(versionHandler);
            router.route("/currency-rates").handler(currencyRatesHandler);
            if (cacheNotificationHandler != null) {
                router.route("/storedrequests/openrtb2").handler(cacheNotificationHandler);
            }
            if (ampCacheNotificationHandler != null) {
                router.route("/storedrequests/amp").handler(ampCacheNotificationHandler);
            }

            contextRunner.<HttpServer>runOnServiceContext(future ->
                    vertx.createHttpServer().requestHandler(router).listen(adminPort, future));

            logger.info("Successfully started Admin Server");
        }
    }
}
