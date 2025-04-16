package org.prebid.server.cache.model;

import lombok.Value;

/**
 * Holds HTTP response info.
 */
@Value(staticConstructor = "of")
public class CacheHttpResponse {

    int statusCode;

    String body;
}
