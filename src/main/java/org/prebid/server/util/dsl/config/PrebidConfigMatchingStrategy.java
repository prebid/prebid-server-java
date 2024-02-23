package org.prebid.server.util.dsl.config;

public interface PrebidConfigMatchingStrategy {

    String match(PrebidConfigSource source, PrebidConfigParameters parameters);
}
