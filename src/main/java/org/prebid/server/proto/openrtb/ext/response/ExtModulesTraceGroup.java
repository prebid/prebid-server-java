package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtModulesTraceGroup {

    @JsonProperty("executiontimemillis")
    Long executionTime;

    @JsonProperty("invocationresults")
    List<ExtModulesTraceInvocationResult> invocationResults;
}
