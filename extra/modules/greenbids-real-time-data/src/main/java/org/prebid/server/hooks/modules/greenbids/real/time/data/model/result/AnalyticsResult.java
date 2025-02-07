package org.prebid.server.hooks.modules.greenbids.real.time.data.model.result;

import lombok.Value;
import org.prebid.server.analytics.reporter.greenbids.model.Ortb2ImpExtResult;

import java.util.Map;

@Value(staticConstructor = "of")
public class AnalyticsResult {

    String status;

    Map<String, Ortb2ImpExtResult> values;
}
