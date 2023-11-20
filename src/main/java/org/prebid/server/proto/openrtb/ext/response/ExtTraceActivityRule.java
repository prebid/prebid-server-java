package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;
import org.prebid.server.activity.infrastructure.rule.Rule;

@Value(staticConstructor = "of")
public class ExtTraceActivityRule implements ExtTraceActivityInfrastructure {

    String description;

    JsonNode ruleConfiguration;

    Rule.Result result;
}
