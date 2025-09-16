package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtModulesTraceAnalyticsResult {

    String status;

    ObjectNode values;

    @JsonProperty("appliedto")
    ExtModulesTraceAnalyticsAppliedTo appliedTo;
}
