package org.prebid.server.cache.model;

import lombok.Value;

/**
 * Holds HTTP request info.
 */
@Value(staticConstructor = "of")
public class CacheHttpRequest {

    String uri;

    String body;
}
