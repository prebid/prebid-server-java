package org.prebid.server.analytics.reporter.agma.model;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Builder
@Value
public class AgmaAnalyticsProperties {

    String url;

    boolean gzip;

    Integer bufferSize;

    Integer maxEventsCount;

    Long bufferTimeoutMs;

    Long httpTimeoutMs;

    Map<String, String> accounts;

}
