package org.prebid.server.privacy.gdpr.model;

import lombok.Value;
import org.prebid.server.metric.MetricName;

@Value(staticConstructor = "of")
public class RequestLogInfo {

    MetricName requestType;

    String source;

    String accountId;
}
