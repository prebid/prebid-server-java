package org.prebid.server.proto.openrtb.ext.response;

import lombok.Value;
import org.prebid.server.activity.infrastructure.rule.Rule;

@Value(staticConstructor = "of")
public class ExtTraceActivityRule implements ExtTraceActivityInfrastructure {

    String description = "Processing rule.";

    Object ruleConfiguration;

    Rule.Result result;
}
