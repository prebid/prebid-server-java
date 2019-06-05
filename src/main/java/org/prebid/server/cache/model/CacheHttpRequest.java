package org.prebid.server.cache.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Holds HTTP request info.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class CacheHttpRequest {

    String uri;

    String body;
}
