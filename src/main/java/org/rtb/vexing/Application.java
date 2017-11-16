package org.rtb.vexing;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.StaticHandler;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.experimental.FieldDefaults;
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.adapter.PreBidRequestContextFactory;
import org.rtb.vexing.cache.CacheService;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.handler.AuctionHandler;
import org.rtb.vexing.handler.CookieSyncHandler;
import org.rtb.vexing.handler.SetuidHandler;
import org.rtb.vexing.handler.StatusHandler;
import org.rtb.vexing.json.ObjectMapperConfigurer;
import org.rtb.vexing.metric.Metrics;
import org.rtb.vexing.metric.ReporterFactory;
import org.rtb.vexing.settings.ApplicationSettings;
import org.rtb.vexing.vertx.CloseableAdapter;

import java.io.IOException;
import java.util.Properties;

public class Application extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    private static final String STATIC_WEBROOT = "static";

    /**
     * Start the verticle instance.
     */
    @Override
    public void start(Future<Void> startFuture) {
        ApplicationConfig.create(vertx, "/default-conf.json")
                .compose(this::initialize)
                .compose(
                        httpServer -> {
                            logger.debug("Vexing server has been started successfully");
                            startFuture.complete();
                        },
                        startFuture);
    }

    private Future<HttpServer> initialize(ApplicationConfig config) {
        final DependencyContext dependencyContext = DependencyContext.create(vertx, config);

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
        // FIXME: remove
        Json.prettyMapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .registerModule(new AfterburnerModule());
    }

    /**
     * Create a {@link Router} with all the supported endpoints for this application.
     */
    private Router routes(DependencyContext dependencyContext) {
        final Router router = Router.router(vertx);
        router.route().handler(CookieHandler.create());
        router.route().handler(BodyHandler.create());
        router.post("/auction").handler(dependencyContext.auctionHandler::auction);
        router.get("/status").handler(dependencyContext.statusHandler::status);
        router.post("/cookie_sync").handler(dependencyContext.cookieSyncHandler::sync);
        router.get("/setuid").handler(dependencyContext.setuidHandler::setuid);

        StaticHandler staticHandler = StaticHandler.create(STATIC_WEBROOT).setCachingEnabled(false);
        router.get("/static/*").handler(staticHandler);
        router.get("/").handler(staticHandler); // serves index.html by default

        return router;
    }

    @Builder
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class DependencyContext {

        ApplicationSettings applicationSettings;
        MetricRegistry metricRegistry;
        Metrics metrics;
        AuctionHandler auctionHandler;
        StatusHandler statusHandler;
        CookieSyncHandler cookieSyncHandler;
        SetuidHandler setuidHandler;

        public static DependencyContext create(Vertx vertx, ApplicationConfig config) {
            final HttpClient httpClient = httpClient(vertx, config);
            final ApplicationSettings applicationSettings = ApplicationSettings.create(vertx, config);
            final MetricRegistry metricRegistry = new MetricRegistry();
            configureMetricsReporter(metricRegistry, config, vertx);
            final Metrics metrics = Metrics.create(metricRegistry, config);
            final AdapterCatalog adapterCatalog = AdapterCatalog.create(config, httpClient, metrics);
            final CacheService cacheService = CacheService.create(httpClient, config);

            return builder()
                    .applicationSettings(applicationSettings)
                    .metricRegistry(metricRegistry)
                    .metrics(metrics)
                    .auctionHandler(new AuctionHandler(applicationSettings, adapterCatalog,
                            PreBidRequestContextFactory.create(config, psl()), cacheService, vertx, metrics))
                    .statusHandler(new StatusHandler())
                    .cookieSyncHandler(new CookieSyncHandler(adapterCatalog, metrics))
                    .setuidHandler(new SetuidHandler())
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
