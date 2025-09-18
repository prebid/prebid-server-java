package org.prebid.server.analytics.reporter.liveintent.model;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class LiveIntentAnalyticsProperties {

    String partnerId;

    String analyticsEndpoint;

    long timeoutMs;
}
