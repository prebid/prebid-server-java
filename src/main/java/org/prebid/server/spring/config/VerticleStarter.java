package org.prebid.server.spring.config;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import org.prebid.server.vertx.ContextRunner;
import org.prebid.server.vertx.verticles.VerticleDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.List;

@Configuration
public class VerticleStarter {

    @Autowired
    private Vertx vertx;

    @Autowired
    private ContextRunner contextRunner;

    @Autowired
    private List<VerticleDefinition> definitions;

    @EventListener(ContextRefreshedEvent.class)
    public void start() {
        for (VerticleDefinition definition : definitions) {
            if (definition.getAmount() <= 0) {
                continue;
            }

            contextRunner.<String>runBlocking(promise ->
                    vertx.deployVerticle(
                            definition.getFactory(),
                            new DeploymentOptions().setInstances(definition.getAmount()),
                            promise));
        }
    }
}
