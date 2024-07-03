package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.hooks.execution.model.Stage;

@Value(staticConstructor = "of")
public class ExtAnalyticsTags {

    Stage stage;

    String module;

    @JsonProperty("analyticstags")
    ExtModulesTraceAnalyticsTags analyticsTags;
}
