package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class AnalyticsResult {

    String status;

    Map<String, Ortb2ImpExtResult> values;

    String bidder;

    String impId;
}
