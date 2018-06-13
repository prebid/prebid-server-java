package org.prebid.server;

import io.vertx.core.Verticle;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.vertx.ContextRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;

@SpringBootApplication
public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private final PrebidVerticle prebidVerticle;
    private final ContextRunner contextRunner;
    private final int verticleInstances;
    private final long verticleDeployTimeout;

    @Autowired
    public Application(PrebidVerticle prebidVerticle, ContextRunner contextRunner,
                       @Value("${vertx.verticle.instances}") int verticleInstances,
                       @Value("${vertx.verticle.deploy-timeout-ms}") long verticleDeployTimeout) {
        this.prebidVerticle = prebidVerticle;
        this.contextRunner = contextRunner;
        this.verticleInstances = verticleInstances;
        this.verticleDeployTimeout = verticleDeployTimeout;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @PostConstruct
    public void deployVerticles() {
        deployVerticle(verticleInstances, prebidVerticle);

        // skip deploy if bean doesn't exist in application context
        //        if (!prebidVerticle.getBeansOfType(SettingsConfiguration.CacheNotificationConfiguration.class)
        // .isEmpty()) {
        //            deployVerticle(1, CacheNotificationVerticle.class);
        //        }
    }

    private void deployVerticle(int numInstances, Verticle verticle) {
        final String verticleName = verticle.getClass().getSimpleName();

        logger.info("Prebid-server is starting {0} instances of {1}", numInstances, verticleName);

        contextRunner.runOnNewContext(numInstances, future -> {
            try {
                verticle.start(future);
            } catch (Exception e) {
                // should never happen
            }
        }, verticleDeployTimeout);

        logger.info("Prebid-server successfully started {0} instances of {1}", numInstances, verticleName);
    }
}
