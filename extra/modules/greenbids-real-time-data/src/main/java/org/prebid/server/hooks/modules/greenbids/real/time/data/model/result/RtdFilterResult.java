package org.prebid.server.hooks.modules.greenbids.real.time.data.model.result;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class RtdFilterResult {

    String value;

    List<AnalyticsResult> analyticsResults;
}
