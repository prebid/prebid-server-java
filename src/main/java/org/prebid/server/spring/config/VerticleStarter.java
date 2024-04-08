package org.prebid.server.spring.config;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import org.prebid.server.vertx.ContextRunner;
import org.prebid.server.vertx.verticles.VerticleDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class VerticleStarter {

    @Autowired
    public void start(Vertx vertx, ContextRunner contextRunner, List<VerticleDefinition> definitions) {
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
