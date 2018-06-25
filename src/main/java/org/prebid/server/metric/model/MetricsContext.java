package org.prebid.server.metric.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.metric.MetricName;

@AllArgsConstructor(staticName = "of")
@Value
public class MetricsContext {

    MetricName requestType;
}
