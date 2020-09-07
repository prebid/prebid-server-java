package org.prebid.server.cache.model;

import lombok.Builder;
import lombok.Value;

/**
 * Holds HTTP interaction related data.
 */
@Value
@Builder
public class DebugHttpCall {

    String endpoint;

    String requestUri;

    String requestBody;

    Integer responseStatus;

    String responseBody;

    Integer responseTimeMillis;
}
