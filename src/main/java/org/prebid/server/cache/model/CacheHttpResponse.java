package org.prebid.server.cache.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Holds HTTP response info.
 */

@AllArgsConstructor(staticName = "of")
@Value
public class CacheHttpResponse {

    int statusCode;

    String body;
}
