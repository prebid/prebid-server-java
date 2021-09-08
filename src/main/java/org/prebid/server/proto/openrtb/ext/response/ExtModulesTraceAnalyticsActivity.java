package org.prebid.server.proto.openrtb.ext.response;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtModulesTraceAnalyticsActivity {

    String name;

    String status;

    List<ExtModulesTraceAnalyticsResult> results;
}
