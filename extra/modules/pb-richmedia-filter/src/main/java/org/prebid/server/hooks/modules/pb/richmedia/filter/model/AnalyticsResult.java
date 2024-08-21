package org.prebid.server.hooks.modules.pb.richmedia.filter.model;

import lombok.Value;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class AnalyticsResult {

    String status;

    Map<String, Object> values;

    String bidder;

    List<String> impId;
}
