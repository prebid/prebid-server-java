package org.prebid.server;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.spring.config.DeployVerticleException;
import org.prebid.server.spring.config.SettingsConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private final ApplicationContext applicationContext;
    private final Vertx vertx;
    private final int verticleInstances;
    private final long verticleDeployTimeout;

    @Autowired
    public Application(ApplicationContext applicationContext, Vertx vertx,
                       @Value("${vertx.verticle.instances}") int verticleInstances,
                       @Value("${vertx.verticle.deploy-timeout-ms}") long verticleDeployTimeout) {
        this.applicationContext = applicationContext;
        this.vertx = vertx;
        this.verticleInstances = verticleInstances;
        this.verticleDeployTimeout = verticleDeployTimeout;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @EventListener
    public void deployVerticles(ApplicationReadyEvent event) {
        deployVerticle(verticleInstances, PrebidVerticle.class);

        // skip deploy if bean doesn't exist in application context
        if (!applicationContext.getBeansOfType(SettingsConfiguration.CacheNotificationConfiguration.class).isEmpty()) {
            deployVerticle(1, CacheNotificationVerticle.class);
        }
    }

    private void deployVerticle(int numInstances, Class<? extends Verticle> verticleClass) {
        final String verticleName = verticleClass.getSimpleName();

        logger.info("Prebid-server is starting {0} instances of {1}", numInstances, verticleName);

        runOnNewVertxContext(numInstances, future -> {
            try {
                applicationContext.getBean(verticleClass).start(future);
            } catch (Exception e) {
                // should never happen
            }
        });

        logger.info("Prebid-server successfully started {0} instances of {1}", numInstances, verticleName);
    }

    private void runOnNewVertxContext(int times, Handler<Future<Void>> action) {
        final CountDownLatch completionLatch = new CountDownLatch(times);
        final AtomicBoolean actionFailed = new AtomicBoolean(false);

        for (int i = 0; i < times; i++) {
            final Context context = vertx.getOrCreateContext();
            context.runOnContext(v -> action.handle(Future.<Void>future().setHandler(ar -> {
                if (ar.failed()) {
                    actionFailed.compareAndSet(false, true);
                }
                completionLatch.countDown();
            })));
        }

        try {
            if (!completionLatch.await(verticleDeployTimeout, TimeUnit.MILLISECONDS)) {
                throw new DeployVerticleException(
                        "Prebid-server failed due to timeout while waiting for verticle deployments");
            } else if (actionFailed.get()) {
                throw new DeployVerticleException("Prebid-server failed while deploying verticles");
            }
        } catch (InterruptedException e) {
            throw new DeployVerticleException(e);
        }
    }
}
