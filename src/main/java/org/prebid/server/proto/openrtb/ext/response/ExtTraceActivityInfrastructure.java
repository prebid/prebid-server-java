package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
        @JsonSubTypes.Type(ExtTraceActivityInvocation.class),
        @JsonSubTypes.Type(ExtTraceActivityInvocationDefaultResult.class),
        @JsonSubTypes.Type(ExtTraceActivityRule.class),
        @JsonSubTypes.Type(ExtTraceActivityInvocationResult.class)
})
public sealed interface ExtTraceActivityInfrastructure permits
        ExtTraceActivityInvocation,
        ExtTraceActivityInvocationDefaultResult,
        ExtTraceActivityRule,
        ExtTraceActivityInvocationResult {

    @JsonProperty(index = 0)
    String getDescription();
}
