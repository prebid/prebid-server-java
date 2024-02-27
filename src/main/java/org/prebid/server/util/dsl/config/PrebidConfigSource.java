package org.prebid.server.util.dsl.config;

public interface PrebidConfigSource extends PrebidConfigSchema {

    Iterable<String> rules();
}
