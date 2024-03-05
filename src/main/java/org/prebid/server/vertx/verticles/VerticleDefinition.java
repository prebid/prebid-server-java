package org.prebid.server.vertx.verticles;

import io.vertx.core.Verticle;
import lombok.Value;

import java.util.function.Supplier;

@Value(staticConstructor = "of")
public class VerticleDefinition {

    Supplier<Verticle> factory;

    int amount;

    public static VerticleDefinition ofSingleInstance(Supplier<Verticle> factory) {
        return of(factory, 1);
    }

    public static VerticleDefinition ofMultiInstance(Supplier<Verticle> factory, int amount) {
        return of(factory, amount);
    }
}
