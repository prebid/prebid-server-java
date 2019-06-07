package org.prebid.server.cache.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Holds HTTP interaction related data.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class CacheHttpCall {

    private static final CacheHttpCall EMPTY = CacheHttpCall.of(null, null, null);

    CacheHttpRequest request;

    CacheHttpResponse response;

    Integer responseTimeMillis;

    public static CacheHttpCall empty() {
        return EMPTY;
    }
}
