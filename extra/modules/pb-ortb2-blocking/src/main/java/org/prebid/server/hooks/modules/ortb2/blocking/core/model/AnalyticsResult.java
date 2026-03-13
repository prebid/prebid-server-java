package org.prebid.server.hooks.modules.ortb2.blocking.core.model;

import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class AnalyticsResult {

    String status;

    Map<String, Object> values;

    String bidder;

    String impId;
}
