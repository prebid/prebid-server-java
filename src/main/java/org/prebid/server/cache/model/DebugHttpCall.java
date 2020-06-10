package org.prebid.server.cache.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Holds HTTP interaction related data.
 */
@Value
@AllArgsConstructor(staticName = "of")
public class DebugHttpCall {

    CacheHttpRequest request;

    CacheHttpResponse response;

    String uri;

    Integer responseTimeMillis;
}
