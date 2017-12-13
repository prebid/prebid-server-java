package org.rtb.vexing;

import com.codahale.metrics.MetricRegistry;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.experimental.FieldDefaults;
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.auction.PreBidRequestContextFactory;
import org.rtb.vexing.cache.CacheService;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.cookie.UidsCookieService;
import org.rtb.vexing.handler.AuctionHandler;
import org.rtb.vexing.handler.CookieSyncHandler;
import org.rtb.vexing.handler.GetuidsHandler;
import org.rtb.vexing.handler.NoCacheHandler;
import org.rtb.vexing.handler.OptoutHandler;
import org.rtb.vexing.handler.SetuidHandler;
import org.rtb.vexing.handler.StatusHandler;
import org.rtb.vexing.json.ObjectMapperConfigurer;
import org.rtb.vexing.metric.Metrics;
import org.rtb.vexing.metric.ReporterFactory;
import org.rtb.vexing.optout.GoogleRecaptchaVerifier;
import org.rtb.vexing.settings.ApplicationSettings;
import org.rtb.vexing.vertx.CloseableAdapter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;

public class Application extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    /**
     * Start the verticle instance.
     */
    @Override
    public void start(Future<Void> startFuture) {
        ApplicationConfig.create(vertx, "/default-conf.json")
                .compose(config -> ApplicationSettings.create(vertx, config)
                        .compose(settings -> initialize(config, settings)))
                .compose(
                        httpServer -> {
                            logger.debug("Vexing server has been started successfully");
                            startFuture.complete();
                        },
                        startFuture);
    }

    private Future<HttpServer> initialize(ApplicationConfig config, ApplicationSettings applicationSettings) {
        final DependencyContext dependencyContext = DependencyContext.create(vertx, config, applicationSettings);

        configureJSON();

        final Router router = routes(dependencyContext);

        final Future<HttpServer> httpServerFuture = Future.future();
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(config.getInteger("http.port"), httpServerFuture);

        return httpServerFuture;
    }

    /**
     * Configure the {@link Json#mapper} to be used for all JSON serialization.
     */
    private void configureJSON() {
        ObjectMapperConfigurer.configure();
    }

    /**
     * Create a {@link Router} with all the supported endpoints for this application.
     */
    private Router routes(DependencyContext dependencyContext) {
        final Router router = Router.router(vertx);
        router.route().handler(CookieHandler.create());
        router.route().handler(BodyHandler.create());
        router.route().handler(NoCacheHandler.create());
        router.route().handler(CorsHandler.create(".*")
                .allowCredentials(true)
                .allowedHeaders(new HashSet<>(Arrays.asList(HttpHeaders.ORIGIN.toString(),
                        HttpHeaders.ACCEPT.toString(), HttpHeaders.CONTENT_TYPE.toString())))
                .allowedMethods(new HashSet<>(Arrays.asList(HttpMethod.GET, HttpMethod.POST, HttpMethod.HEAD,
                        HttpMethod.OPTIONS))));
        router.post("/auction").handler(dependencyContext.auctionHandler::auction);
        router.get("/status").handler(dependencyContext.statusHandler::status);
        router.post("/cookie_sync").handler(dependencyContext.cookieSyncHandler::sync);
        router.get("/setuid").handler(dependencyContext.setuidHandler::setuid);
        router.get("/getuids").handler(dependencyContext.getuidsHandler::getuids);
        router.post("/optout").handler(dependencyContext.optoutHandler::optout);
        router.get("/optout").handler(dependencyContext.optoutHandler::optout);

        final StaticHandler staticHandler = StaticHandler.create("static").setCachingEnabled(false);
        router.get("/static/*").handler(staticHandler);
        router.get("/").handler(staticHandler); // serves index.html by default

        return router;
    }

    @Builder
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class DependencyContext {

        AuctionHandler auctionHandler;
        StatusHandler statusHandler;
        CookieSyncHandler cookieSyncHandler;
        SetuidHandler setuidHandler;
        GetuidsHandler getuidsHandler;
        OptoutHandler optoutHandler;

        public static DependencyContext create(Vertx vertx, ApplicationConfig config,
                                               ApplicationSettings applicationSettings) {
            final HttpClient httpClient = httpClient(vertx, config);
            final MetricRegistry metricRegistry = new MetricRegistry();
            configureMetricsReporter(metricRegistry, config, vertx);
            final Metrics metrics = Metrics.create(metricRegistry, config);
            final AdapterCatalog adapterCatalog = AdapterCatalog.create(config, httpClient);
            final UidsCookieService uidsCookieService = UidsCookieService.create(config);
            final PreBidRequestContextFactory preBidRequestContextFactory =
                    PreBidRequestContextFactory.create(config, psl(), applicationSettings, uidsCookieService);
            final CacheService cacheService = CacheService.create(httpClient, config);
            final GoogleRecaptchaVerifier googleRecaptchaVerifier = GoogleRecaptchaVerifier.create(httpClient, config);

            return builder()
                    .auctionHandler(new AuctionHandler(applicationSettings, adapterCatalog,
                            preBidRequestContextFactory, cacheService, vertx, metrics))
                    .statusHandler(new StatusHandler())
                    .cookieSyncHandler(new CookieSyncHandler(uidsCookieService, adapterCatalog, metrics))
                    .setuidHandler(new SetuidHandler(uidsCookieService, metrics))
                    .getuidsHandler(new GetuidsHandler(uidsCookieService))
                    .optoutHandler(OptoutHandler.create(config, googleRecaptchaVerifier, uidsCookieService))
                    .build();
        }

        private static PublicSuffixList psl() {
            final PublicSuffixListFactory factory = new PublicSuffixListFactory();

            Properties properties = factory.getDefaults();
            properties.setProperty(PublicSuffixListFactory.PROPERTY_LIST_FILE, "/effective_tld_names.dat");
            try {
                return factory.build(properties);
            } catch (IOException | ClassNotFoundException e) {
                throw new IllegalArgumentException("Could not initialize public suffix list", e);
            }
        }

        private static HttpClient httpClient(Vertx vertx, ApplicationConfig config) {
            final HttpClientOptions options = new HttpClientOptions()
                    .setMaxPoolSize(config.getInteger("http-client.max-pool-size"))
                    .setConnectTimeout(config.getInteger("http-client.connect-timeout-ms"));
            return vertx.createHttpClient(options);
        }

        private static void configureMetricsReporter(MetricRegistry metricRegistry, ApplicationConfig config,
                                                     Vertx vertx) {
            ReporterFactory.create(metricRegistry, config)
                    .map(CloseableAdapter::new)
                    .ifPresent(closeable -> vertx.getOrCreateContext().addCloseHook(closeable));
        }
    }
}
