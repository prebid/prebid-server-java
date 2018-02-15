package org.rtb.vexing.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.adapter.HttpConnector;
import org.rtb.vexing.auction.ExchangeService;
import org.rtb.vexing.auction.PreBidRequestContextFactory;
import org.rtb.vexing.auction.StoredRequestProcessor;
import org.rtb.vexing.cache.CacheService;
import org.rtb.vexing.cookie.UidsCookieService;
import org.rtb.vexing.handler.AuctionHandler;
import org.rtb.vexing.handler.BidderParamHandler;
import org.rtb.vexing.handler.CookieSyncHandler;
import org.rtb.vexing.handler.GetuidsHandler;
import org.rtb.vexing.handler.IpHandler;
import org.rtb.vexing.handler.NoCacheHandler;
import org.rtb.vexing.handler.OptoutHandler;
import org.rtb.vexing.handler.SetuidHandler;
import org.rtb.vexing.handler.StatusHandler;
import org.rtb.vexing.metric.Metrics;
import org.rtb.vexing.optout.GoogleRecaptchaVerifier;
import org.rtb.vexing.settings.ApplicationSettings;
import org.rtb.vexing.validation.BidderParamValidator;
import org.rtb.vexing.validation.RequestValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;

@Configuration
public class WebConfiguration {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    Router router(Vertx vertx,
                  CookieHandler cookieHandler,
                  BodyHandler bodyHandler,
                  NoCacheHandler noCacheHandler,
                  CorsHandler corsHandler,
                  AuctionHandler auctionHandler,
                  org.rtb.vexing.handler.openrtb2.AuctionHandler openrtbAuctionHandler,
                  StatusHandler statusHandler,
                  CookieSyncHandler cookieSyncHandler,
                  SetuidHandler setuidHandler,
                  GetuidsHandler getuidsHandler,
                  OptoutHandler optoutHandler,
                  IpHandler ipHandler,
                  BidderParamHandler bidderParamHandler,
                  StaticHandler staticHandler) {

        final Router router = Router.router(vertx);

        router.route().handler(cookieHandler);
        router.route().handler(bodyHandler);
        router.route().handler(noCacheHandler);
        router.route().handler(corsHandler);
        router.post("/auction").handler(auctionHandler);
        router.post("/openrtb2/auction").handler(openrtbAuctionHandler);
        router.get("/status").handler(statusHandler);
        router.post("/cookie_sync").handler(cookieSyncHandler);
        router.get("/setuid").handler(setuidHandler);
        router.get("/getuids").handler(getuidsHandler);
        router.post("/optout").handler(optoutHandler);
        router.get("/optout").handler(optoutHandler);
        router.get("/ip").handler(ipHandler);
        router.get("/bidders/params").handler(bidderParamHandler);
        router.get("/static/*").handler(staticHandler);
        router.get("/").handler(staticHandler); // serves indexAdapter.html by default

        return router;
    }

    @Bean
    CookieHandler cookieHandler() {
        return CookieHandler.create();
    }

    @Bean
    BodyHandler bodyHandler() {
        return BodyHandler.create();
    }

    @Bean
    NoCacheHandler noCacheHandler() {
        return NoCacheHandler.create();
    }

    @Bean
    CorsHandler corsHandler() {
        return CorsHandler.create(".*")
                .allowCredentials(true)
                .allowedHeaders(new HashSet<>(Arrays.asList(HttpHeaders.ORIGIN.toString(),
                        HttpHeaders.ACCEPT.toString(), HttpHeaders.CONTENT_TYPE.toString())))
                .allowedMethods(new HashSet<>(Arrays.asList(HttpMethod.GET, HttpMethod.POST, HttpMethod.HEAD,
                        HttpMethod.OPTIONS)));
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    AuctionHandler auctionHandler(
            ApplicationSettings applicationSettings,
            AdapterCatalog adapterCatalog,
            PreBidRequestContextFactory preBidRequestContextFactory,
            CacheService cacheService,
            Vertx vertx,
            Metrics metrics,
            HttpConnector httpConnector) {

        return new AuctionHandler(applicationSettings, adapterCatalog, preBidRequestContextFactory,
                cacheService, vertx, metrics, httpConnector);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    org.rtb.vexing.handler.openrtb2.AuctionHandler openrtbAuctionHandler(
            @Value("${auction.max-request-size}") int maxRequestSize,
            @Value("${auction.default-timeout-ms}") int defaultTimeoutMs,
            RequestValidator requestValidator,
            ExchangeService exchangeService,
            StoredRequestProcessor storedRequestProcessor,
            PreBidRequestContextFactory preBidRequestContextFactory,
            UidsCookieService uidsCookieService) {

        return new org.rtb.vexing.handler.openrtb2.AuctionHandler(maxRequestSize, defaultTimeoutMs, requestValidator,
                exchangeService, storedRequestProcessor, preBidRequestContextFactory, uidsCookieService);
    }

    @Bean
    StatusHandler statusHandler() {
        return new StatusHandler();
    }

    @Bean
    CookieSyncHandler cookieSyncHandler(
            UidsCookieService uidsCookieService,
            AdapterCatalog adapterCatalog,
            Metrics metrics) {

        return new CookieSyncHandler(uidsCookieService, adapterCatalog, metrics);
    }

    @Bean
    SetuidHandler setuidHandler(UidsCookieService uidsCookieService, Metrics metrics) {
        return new SetuidHandler(uidsCookieService, metrics);
    }

    @Bean
    GetuidsHandler getuidsHandler(UidsCookieService uidsCookieService) {
        return new GetuidsHandler(uidsCookieService);
    }

    @Bean
    OptoutHandler optoutHandler(
            @Value("${external-url}") String externalUrl,
            @Value("${host-cookie.opt-out-url}") String optoutUrl,
            @Value("${host-cookie.opt-in-url}") String optinUrl,
            GoogleRecaptchaVerifier googleRecaptchaVerifier,
            UidsCookieService uidsCookieService) {
        return new OptoutHandler(googleRecaptchaVerifier,
                uidsCookieService,
                OptoutHandler.getOptoutRedirectUrl(externalUrl),
                validateUrl(optoutUrl),
                validateUrl(optinUrl)
        );
    }

    @Bean
    IpHandler ipHandler() {
        return new IpHandler();
    }

    @Bean
    BidderParamHandler bidderParamHandler(BidderParamValidator bidderParamValidator) {
        return new BidderParamHandler(bidderParamValidator);
    }

    @Bean
    StaticHandler staticHandler() {
        return StaticHandler.create("static").setCachingEnabled(false);
    }

    private static String validateUrl(String url) {
        try {
            return new URL(url).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(String.format("Could not get url from string: %s", url), e);
        }
    }
}
