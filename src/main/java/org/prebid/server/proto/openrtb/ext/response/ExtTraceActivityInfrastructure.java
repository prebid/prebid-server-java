package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public sealed interface ExtTraceActivityInfrastructure permits
        ExtTraceActivityInvocation,
        ExtTraceActivityInvocationDefaultResult,
        ExtTraceActivityRule,
        ExtTraceActivityInvocationResult {

    @JsonProperty(index = 0)
    String getDescription();
}
