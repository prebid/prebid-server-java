package org.prebid.server.analytics.reporter.agma.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AgmaAccountAnalyticsProperties {

    String code;

    String publisherId;

    String siteAppId;
}
