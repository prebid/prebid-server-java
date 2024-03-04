package org.prebid.server.vertx.verticles;

import lombok.Value;

import java.util.function.Supplier;

@Value(staticConstructor = "of")
public class VerticleDefinition {

    Supplier<InitializableVerticle> factory;

    int amount;

    public static VerticleDefinition ofSingleInstance(Supplier<InitializableVerticle> factory) {
        return of(factory, 1);
    }

    public static VerticleDefinition ofMultiInstance(Supplier<InitializableVerticle> factory, int amount) {
        return of(factory, amount);
    }
}
