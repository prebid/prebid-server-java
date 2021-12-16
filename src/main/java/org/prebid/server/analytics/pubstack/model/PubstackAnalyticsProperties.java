package org.prebid.server.analytics.pubstack.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class PubstackAnalyticsProperties {

    String endpoint;

    String scopeId;

    Boolean enabled;

    Long configurationRefreshDelayMs;

    Integer sizeBytes;

    Integer count;

    Long reportTtlMs;

    Long timeoutMs;
}
